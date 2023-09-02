package net.corda.flow.pipeline.impl

import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.FlowEventExceptionProcessor
import net.corda.flow.pipeline.converters.FlowEventContextConverter
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
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig
import net.corda.schema.configuration.FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Instant
import net.corda.flow.fiber.cache.FlowFiberCache

@Suppress("Unused" , "TooManyFunctions")
@Component(service = [FlowEventExceptionProcessor::class])
class FlowEventExceptionProcessorImpl @Activate constructor(
    @Reference(service = FlowMessageFactory::class)
    private val flowMessageFactory: FlowMessageFactory,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory,
    @Reference(service = FlowEventContextConverter::class)
    private val flowEventContextConverter: FlowEventContextConverter,
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowFiberCache::class)
    private val flowFiberCache: FlowFiberCache,
) : FlowEventExceptionProcessor {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var maxRetryAttempts = 0

    override fun configure(config: SmartConfig) {
        maxRetryAttempts = config.getInt(PROCESSING_MAX_RETRY_ATTEMPTS)
    }

    override fun process(throwable: Throwable): StateAndEventProcessor.Response<Checkpoint> {
        log.warn("Unexpected exception while processing flow, the flow will be sent to the DLQ", throwable)
        return StateAndEventProcessor.Response(
            updatedState = null,
            responseEvents = listOf(),
            markForDLQ = true
        )
    }

    override fun process(
        exception: FlowTransientException,
        context: FlowEventContext<*>
    ): StateAndEventProcessor.Response<Checkpoint> {
        return withEscalation {
            val flowCheckpoint = context.checkpoint

            /** If we have reached the maximum number of retries then we escalate this to a fatal
             * exception and DLQ the flow
             */
            if (flowCheckpoint.currentRetryCount >= maxRetryAttempts) {
                return@withEscalation process(
                    FlowFatalException(
                        "Execution failed with \"${exception.message}\" after $maxRetryAttempts retry attempts.",
                        exception
                    ), context
                )
            }

            log.debug {
                "A transient exception was thrown the event that failed will be retried. event='${context.inputEvent}',  $exception"
            }

            /**
             * When retrying, the flow engine switches on whether to retry a previous event on whether the current input
             * is a Wakeup or not. The mechanism for generating the Wakeup events that would trigger a retry has been
             * removed, however, so publishing a Wakeup here is required to keep the retry feature alive.
             *
             * This is really only a temporary solution and a bit of a hack. Longer term this should be replaced with
             * the new scheduler mechanism. It may also be possible to remove all sources of transient exceptions if
             * the state storage solution is changed, which would allow this feature to be removed entirely.
             */
            val payload = context.inputEventPayload ?: return@withEscalation process(
                FlowFatalException(
                    "Could not process a retry as the input event has no payload.",
                    exception
                ), context
            )
            val records = createStatusRecord(context.checkpoint.flowId) {
                flowMessageFactory.createFlowRetryingStatusMessage(context.checkpoint)
            } + flowRecordFactory.createFlowEventRecord(context.checkpoint.flowId, payload)

            // Set up records before the rollback, just in case a transient exception happens after a flow is initialised
            // but before the first checkpoint has been recorded.
            flowCheckpoint.rollback()
            flowCheckpoint.markForRetry(context.inputEvent, exception)

            removeCachedFlowFiber(flowCheckpoint)

            flowEventContextConverter.convert(context.copy(outputRecords = context.outputRecords + records))
        }
    }

    override fun process(
        exception: FlowFatalException,
        context: FlowEventContext<*>
    ): StateAndEventProcessor.Response<Checkpoint> = withEscalation {

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

        val activeSessionIds = getActiveSessionIds(checkpoint)

        if(activeSessionIds.isNotEmpty()) {
            checkpoint.putSessionStates(
                flowSessionManager.sendErrorMessages(
                    context.checkpoint, activeSessionIds, exception, exceptionHandlingStartTime
                )
            )
        }

        val errorEvents = flowSessionManager.getSessionErrorEventRecords(checkpoint, context.flowConfig, exceptionHandlingStartTime)
        val cleanupEvents = createCleanupEventsForSessions(
            getScheduledCleanupExpiryTime(context, exceptionHandlingStartTime),
            checkpoint.sessions.filterNot { it.hasScheduledCleanup }
        )

        removeCachedFlowFiber(checkpoint)
        checkpoint.markDeleted()

        val records = createStatusRecord(checkpoint.flowId) {
            flowMessageFactory.createFlowFailedStatusMessage(
                checkpoint,
                FLOW_FAILED,
                exception.message
            )
        }

        StateAndEventProcessor.Response(
            updatedState = null,
            responseEvents = records + errorEvents + cleanupEvents,
            markForDLQ = true
        )
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
                "Could not create a flow status message for a failed flow with ID $id as " +
                        "the flow start context was missing."
            )
            listOf()
        }
    }

    override fun process(
        exception: FlowEventException,
        context: FlowEventContext<*>
    ): StateAndEventProcessor.Response<Checkpoint> = withEscalation {
        log.warn("A non critical error was reported while processing the event: ${exception.message}")

        removeCachedFlowFiber(context.checkpoint)

        flowEventContextConverter.convert(context)
    }

    override fun process(
        exception: FlowPlatformException,
        context: FlowEventContext<*>
    ): StateAndEventProcessor.Response<Checkpoint> {
        return withEscalation {
            val checkpoint = context.checkpoint

            checkpoint.setPendingPlatformError(PLATFORM_ERROR, exception.message)
            checkpoint.waitingFor = WaitingFor(net.corda.data.flow.state.waiting.Wakeup())

            removeCachedFlowFiber(checkpoint)

            flowEventContextConverter.convert(context)
        }
    }

    override fun process(
        exception: FlowMarkedForKillException,
        context: FlowEventContext<*>
    ): StateAndEventProcessor.Response<Checkpoint> {
        return withEscalation {
            val exceptionHandlingStartTime = Instant.now()
            val checkpoint = context.checkpoint

            if (!checkpoint.doesExist) {
                val statusRecord = createFlowKilledStatusRecordWithoutCheckpoint(
                    checkpoint.flowId,
                    context,
                    exception.message ?: "No exception message provided."
                )

                return@withEscalation flowEventContextConverter.convert(
                    context.copy(
                        outputRecords = statusRecord,
                        sendToDlq = false
                    )
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

            flowEventContextConverter.convert(
                context.copy(
                    outputRecords = errorEvents + cleanupEvents + statusRecord,
                    sendToDlq = false // killed flows do not go to DLQ
                )
            )
        }
    }

    private fun getActiveSessionIds(checkpoint: FlowCheckpoint) = checkpoint.sessions.filterNot { sessionState ->
        sessionState.status == SessionStateType.CLOSED || sessionState.status == SessionStateType.ERROR
    }.map { it.sessionId }

    private fun withEscalation(handler: () -> StateAndEventProcessor.Response<Checkpoint>): StateAndEventProcessor.Response<Checkpoint> {
        return try {
            handler()
        } catch (t: Throwable) {
            // The exception handler failed. Rather than take the whole pipeline down, forcibly DLQ the offending event.
            process(t)
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
                val status = FlowStatus().apply{
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
