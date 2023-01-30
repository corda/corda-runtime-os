package net.corda.flow.pipeline.impl

import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.FlowEventContext
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
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Instant

@Suppress("Unused")
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

            val records = createStatusRecord(context.checkpoint.flowId) {
                flowMessageFactory.createFlowRetryingStatusMessage(context.checkpoint)
            }

            // Set up records before the rollback, just in case a transient exception happens after a flow is initialised
            // but before the first checkpoint has been recorded.
            flowCheckpoint.rollback()
            flowCheckpoint.markForRetry(context.inputEvent, exception)

            flowEventContextConverter.convert(context.copy(outputRecords = context.outputRecords + records))
        }
    }

    override fun process(
        exception: FlowFatalException,
        context: FlowEventContext<*>
    ): StateAndEventProcessor.Response<Checkpoint> = withEscalation {
        val msg = if(!context.checkpoint.doesExist) {
                "Flow processing for flow ID ${context.checkpoint.flowId} has failed due to a fatal exception. " +
                        "doesExist was false"

        } else {
                "Flow processing for flow ID ${context.checkpoint.flowId} has failed due to a fatal exception. " +
                        "Flow start context: ${context.checkpoint.flowStartContext}"
        }
        log.warn(msg, exception)

        val records = createStatusRecord(context.checkpoint.flowId) {
            flowMessageFactory.createFlowFailedStatusMessage(
                context.checkpoint,
                FLOW_FAILED,
                exception.message
            )
        }

        StateAndEventProcessor.Response(
            updatedState = null,
            responseEvents = records,
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

            val record = flowRecordFactory.createFlowEventRecord(checkpoint.flowId, Wakeup())
            flowEventContextConverter.convert(context.copy(outputRecords = context.outputRecords + record))
        }
    }

    override fun process(exception: FlowMarkedForKillException, context: FlowEventContext<*>): StateAndEventProcessor.Response<Checkpoint> {
        return withEscalation {
            val exceptionHandlingStartTime = Instant.now()
            val checkpoint = context.checkpoint

            if (!checkpoint.doesExist) {
                return@withEscalation flowEventContextConverter.convert(
                    context.copy(
                        outputRecords = createFlowKilledStatusRecord(checkpoint, exception.message),
                        sendToDlq = false
                    )
                )
            }

            val activeSessionIds = checkpoint.sessions.filterNot { sessionState ->
                sessionState.status == SessionStateType.CLOSED || sessionState.status == SessionStateType.ERROR
            }.map { it.sessionId }

            if (activeSessionIds.isNotEmpty()) {
                checkpoint.putSessionStates(
                    flowSessionManager.sendErrorMessages(
                        context.checkpoint, activeSessionIds, exception, exceptionHandlingStartTime
                    )
                )
            }
            val cleanupEvents = createCleanupEventsForSessions(
                getScheduledCleanupExpiryTime(context, exceptionHandlingStartTime),
                checkpoint.sessions.filterNot { it.hasScheduledCleanup }
            )
            val statusRecord = createFlowKilledStatusRecord(checkpoint, exception.message)

            checkpoint.markDeleted()

            flowEventContextConverter.convert(
                context.copy(
                    outputRecords = cleanupEvents + statusRecord,
                    sendToDlq = false // killed flows do not go to DLQ
                )
            )
        }
    }

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

    private fun getScheduledCleanupExpiryTime(context: FlowEventContext<*>, now: Instant): Long {
        val flowCleanupTime = context.config.getLong(FlowConfig.SESSION_FLOW_CLEANUP_TIME)
        return now.plusMillis(flowCleanupTime).toEpochMilli()
    }
}
