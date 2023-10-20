package net.corda.flow.pipeline.impl

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.pipeline.FlowEventExceptionProcessor
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowMarkedForKillException
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.exceptions.FlowProcessingExceptionTypes.FLOW_FAILED
import net.corda.flow.pipeline.exceptions.FlowProcessingExceptionTypes.PLATFORM_ERROR
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.utils.toMap
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.getLongOrDefault
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig
import net.corda.schema.configuration.FlowConfig.PROCESSING_MAX_RETRY_WINDOW_DURATION
import net.corda.session.manager.Constants
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

@Suppress("Unused", "TooManyFunctions")
@Component(service = [FlowEventExceptionProcessor::class])
class FlowEventExceptionProcessorImpl @Activate constructor(
    @Reference(service = FlowMessageFactory::class)
    private val flowMessageFactory: FlowMessageFactory,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory,
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowFiberCache::class)
    private val flowFiberCache: FlowFiberCache,
) : FlowEventExceptionProcessor {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val DEFAULT_MAX_RETRY_WINDOW_DURATION_MS = 300000L // 5 minutes
    }

    private var maxRetryWindowDuration = Duration.ZERO

    override fun configure(config: SmartConfig) {
        maxRetryWindowDuration = Duration.ofMillis(
            config.getLongOrDefault(PROCESSING_MAX_RETRY_WINDOW_DURATION, DEFAULT_MAX_RETRY_WINDOW_DURATION_MS)
        )
    }

    override fun process(throwable: Throwable, context: FlowEventContext<*>): FlowEventContext<*> {
        log.warn("Unexpected exception while processing flow, the flow will be sent to the DLQ", throwable)
        context.checkpoint.markDeleted()
        return context.copy(
            outputRecords = listOf(),
            sendToDlq = true
        )
    }

    override fun process(
        exception: FlowTransientException,
        context: FlowEventContext<*>
    ): FlowEventContext<*> {
        return withEscalation(context) {
            val flowCheckpoint = context.checkpoint

            /** If the retry window has expired then we escalate this to a fatal exception and DLQ the flow */
            if (retryWindowExpired(flowCheckpoint.firstFailureTimestamp)) {
                return@withEscalation process(
                    FlowFatalException(
                        "Execution failed with \"${exception.message}\" after " +
                                "${flowCheckpoint.currentRetryCount} retry attempts in a retry window of $maxRetryWindowDuration.",
                        exception
                    ), context
                )
            }

            log.info("Flow ${context.checkpoint.flowId} encountered a transient problem and is retrying: ${exception.message}")

            val payload = context.inputEventPayload ?: return@withEscalation process(
                FlowFatalException(
                    "Could not process a retry as the input event has no payload.",
                    exception
                ), context
            )

            /**
             * As we're still inside the retry window, republish the record that needs retrying here. If the system is
             * under load, there may be some delay before it is retried. This is reasonable however, as the system may
             * need to wait for the underlying transient problem to clear up.
             */
            val records = createStatusRecord(context.checkpoint.flowId) {
                flowMessageFactory.createFlowRetryingStatusMessage(context.checkpoint)
            } + flowRecordFactory.createFlowEventRecord(context.checkpoint.flowId, payload)

            // Set up records before the rollback, just in case a transient exception happens after a flow is initialised
            // but before the first checkpoint has been recorded.
            flowCheckpoint.rollback()
            flowCheckpoint.markForRetry(context.inputEvent, exception)

            removeCachedFlowFiber(flowCheckpoint)

            context.copy(outputRecords = context.outputRecords + records)
        }
    }

    private fun retryWindowExpired(firstFailureTimestamp: Instant?): Boolean {
        return firstFailureTimestamp != null &&
                Duration.between(firstFailureTimestamp, Instant.now()) >= maxRetryWindowDuration
    }

    override fun process(
        exception: FlowFatalException,
        context: FlowEventContext<*>
    ): FlowEventContext<*> = withEscalation(context) {

        val exceptionHandlingStartTime = Instant.now()
        val checkpoint = context.checkpoint

        val msg = if (!checkpoint.doesExist) {
            "Flow processing for flow ID ${checkpoint.flowId} has failed due to a fatal exception. " +
                    "Checkpoint/Flow start context doesn't exist"
        } else {
            "Flow processing for flow ID ${checkpoint.flowId} has failed due to a fatal exception. " +
                    "Flow start context: ${checkpoint.flowStartContext}"
        }
        log.warn(msg, exception)




        if(context.inputEventPayload is SessionEvent) {
            val inputPayload = context.inputEventPayload as SessionEvent
            val sessionId = inputPayload.sessionId
            if(inputPayload.payload is SessionData) {
//                val sessionData = inputPayload.payload as SessionData
                val instant = Instant.now()

                val sessionState = SessionState.newBuilder()
                    .setSessionId(sessionId)
                    .setSessionStartTime(instant)
                    .setLastReceivedMessageTime(instant)
                    .setCounterpartyIdentity(inputPayload.initiatingIdentity)
                    .setReceivedEventsState(SessionProcessState(0, mutableListOf()))
                    .setSendEventsState(SessionProcessState(0, mutableListOf()))
                    .setSessionProperties(inputPayload.contextSessionProperties)
                    .setStatus(SessionStateType.CONFIRMED)
                    .setHasScheduledCleanup(false)
                    .setRequireClose(inputPayload.contextSessionProperties.toMap()[Constants.FLOW_SESSION_REQUIRE_CLOSE].toBoolean())
                    .build()

                val holdingIdentity = inputPayload.initiatedIdentity

//                context.checkpoint.putSessionStates(
                flowSessionManager.sendErrorMessages(
                    context.checkpoint,
                    listOf(sessionId),
                    exception,
                    Instant.now(),
                    sessionState,
                    holdingIdentity
                )
//                )
            } else {
                val activeSessionIds = getActiveSessionIds(checkpoint)

                if(activeSessionIds.isNotEmpty()) {
                    checkpoint.putSessionStates(
                        flowSessionManager.sendErrorMessages(
                            context.checkpoint, activeSessionIds, exception, exceptionHandlingStartTime
                        )
                    )
                }

                val errorEvents =
                    flowSessionManager.getSessionErrorEventRecords(checkpoint, context.flowConfig, exceptionHandlingStartTime)
                val cleanupEvents = createCleanupEventsForSessions(
                    getScheduledCleanupExpiryTime(context, exceptionHandlingStartTime),
                    checkpoint.sessions.filterNot { it.hasScheduledCleanup }
                )

                val records = createStatusRecord(checkpoint.flowId) {
                    flowMessageFactory.createFlowFailedStatusMessage(
                        checkpoint,
                        FLOW_FAILED,
                        exception.message
                    )
                }

                context.copy(
                    outputRecords = records + errorEvents + cleanupEvents,
                    sendToDlq = true
                )
            }
        }

        removeCachedFlowFiber(checkpoint)
        checkpoint.markDeleted()
        context
    }

    private fun createStatusRecord(id: String, statusGenerator: () -> FlowStatus): List<Record<*, *>> {
        return try {
            val status = statusGenerator()
            listOf(flowRecordFactory.createFlowStatusRecord(status))
        } catch (e: IllegalStateException) {
            // Most errors should happen after a flow has been initialised. However, it is possible for
            // initialisation to have not yet happened at the point the failure is hit if it's a session init message
            // and something goes wrong in trying to retrieve the sandbox. In this case we cannot update the status
            // correctly. This shouldn't matter however - in this case we're treating the issue as the flow never
            // starting at all. We'll still log that the error was seen.
            log.warn(
                "Could not create a flow status message for a flow with ID $id as the flow start context was missing."
            )
            listOf()
        }
    }

    override fun process(
        exception: FlowEventException,
        context: FlowEventContext<*>
    ): FlowEventContext<*> = withEscalation(context) {
        log.warn("A non critical error was reported while processing the event: ${exception.message}")

        removeCachedFlowFiber(context.checkpoint)

        context
    }

    override fun process(
        exception: FlowPlatformException,
        context: FlowEventContext<*>
    ): FlowEventContext<*> {
        return withEscalation(context) {
            val checkpoint = context.checkpoint

            checkpoint.setPendingPlatformError(PLATFORM_ERROR, exception.message)
            checkpoint.waitingFor = WaitingFor(net.corda.data.flow.state.waiting.Wakeup())

            removeCachedFlowFiber(checkpoint)

            context
        }
    }

    override fun process(
        exception: FlowMarkedForKillException,
        context: FlowEventContext<*>
    ): FlowEventContext<*> {
        return withEscalation(context) {
            val exceptionHandlingStartTime = Instant.now()
            val checkpoint = context.checkpoint

            if (!checkpoint.doesExist) {
                val statusRecord = createFlowKilledStatusRecordWithoutCheckpoint(
                    checkpoint.flowId,
                    context,
                    exception.message ?: "No exception message provided."
                )

                return@withEscalation context.copy(
                    outputRecords = statusRecord,
                    sendToDlq = false
                )
            }

            val activeSessionIds = getActiveSessionIds(checkpoint)

            if (activeSessionIds.isNotEmpty()) {
                checkpoint.putSessionStates(
                    flowSessionManager.sendErrorMessages(
                        context.checkpoint, activeSessionIds, exception, exceptionHandlingStartTime
                    )
                )
            }
            val errorEvents = flowSessionManager.getSessionErrorEventRecords(
                context.checkpoint,
                context.flowConfig,
                exceptionHandlingStartTime
            )
            val cleanupEvents = createCleanupEventsForSessions(
                getScheduledCleanupExpiryTime(context, exceptionHandlingStartTime),
                checkpoint.sessions.filterNot { it.hasScheduledCleanup }
            )
            val statusRecord =
                createFlowKilledStatusRecord(checkpoint, exception.message ?: "No exception message provided.")

            removeCachedFlowFiber(checkpoint)

            checkpoint.markDeleted()

            context.copy(
                outputRecords = errorEvents + cleanupEvents + statusRecord,
                sendToDlq = false // killed flows do not go to DLQ
            )
        }
    }

    private fun getActiveSessionIds(checkpoint: FlowCheckpoint) = checkpoint.sessions.filterNot { sessionState ->
        sessionState.status == SessionStateType.CLOSED || sessionState.status == SessionStateType.ERROR
    }.map { it.sessionId }

    private fun withEscalation(context: FlowEventContext<*>, handler: () -> FlowEventContext<*>): FlowEventContext<*> {
        return try {
            handler()
        } catch (t: Throwable) {
            // The exception handler failed. Rather than take the whole pipeline down, forcibly DLQ the offending event.
            process(t, context)
        }
    }

    private fun createCleanupEventsForSessions(
        expiryTime: Long,
        sessionsToCleanup: List<SessionState>
    ): List<Record<*, FlowMapperEvent>> {
        return sessionsToCleanup
            .onEach { it.hasScheduledCleanup = true }
            .map {
                flowRecordFactory.createFlowMapperEventRecord(
                    it.sessionId,
                    ScheduleCleanup(expiryTime)
                )
            }
    }

    private fun createFlowKilledStatusRecord(checkpoint: FlowCheckpoint, message: String?): List<Record<*, *>> {
        return createStatusRecord(checkpoint.flowId) {
            flowMessageFactory.createFlowKilledStatusMessage(checkpoint, message)
        }
    }

    private fun createFlowKilledStatusRecordWithoutCheckpoint(
        flowId: String,
        context: FlowEventContext<*>,
        message: String?,
    ): List<Record<*, *>> {
        val inputPayload = context.inputEvent.payload

        return when (inputPayload) {
            is StartFlow -> {
                val status = FlowStatus().apply {
                    key = inputPayload.startContext.statusKey
                    flowStatus = FlowStates.KILLED
                    this.flowId = flowId
                    processingTerminatedReason = message
                }
                listOf(flowRecordFactory.createFlowStatusRecord(status))
            }

            else -> createFlowKilledStatusRecord(
                context.checkpoint, message ?: "No exception message provided."
            )
        }
    }

    private fun getScheduledCleanupExpiryTime(context: FlowEventContext<*>, now: Instant): Long {
        val flowCleanupTime = context.flowConfig.getLong(FlowConfig.SESSION_FLOW_CLEANUP_TIME)
        return now.plusMillis(flowCleanupTime).toEpochMilli()
    }

    /**
     * Remove cached flow fiber for this checkpoint, if it exists.
     */
    private fun removeCachedFlowFiber(checkpoint: FlowCheckpoint) {
        if (checkpoint.doesExist) flowFiberCache.remove(checkpoint.flowKey)
    }
}
