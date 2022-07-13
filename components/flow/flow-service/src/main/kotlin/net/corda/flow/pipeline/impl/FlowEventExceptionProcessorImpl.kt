package net.corda.flow.pipeline.impl

import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowEventExceptionProcessor
import net.corda.flow.pipeline.converters.FlowEventContextConverter
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.exceptions.FlowProcessingException
import net.corda.flow.pipeline.exceptions.FlowProcessingExceptionTypes.FLOW_FAILED
import net.corda.flow.pipeline.exceptions.FlowProcessingExceptionTypes.PLATFORM_ERROR
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("CanBePrimaryConstructorProperty", "Unused")
@Component(service = [FlowEventExceptionProcessor::class])
class FlowEventExceptionProcessorImpl @Activate constructor(
    @Reference(service = FlowMessageFactory::class)
    private val flowMessageFactory: FlowMessageFactory,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory,
    @Reference(service = FlowEventContextConverter::class)
    private val flowEventContextConverter: FlowEventContextConverter,
) : FlowEventExceptionProcessor {

    private companion object {
        val log = contextLogger()
    }

    private var maxRetryAttempts = 0

    override fun configure(config: SmartConfig) {
        maxRetryAttempts = config.getInt(PROCESSING_MAX_RETRY_ATTEMPTS)
    }

    override fun process(exception: Exception): StateAndEventProcessor.Response<Checkpoint> = withEscalation {
        log.error("Unexpected exception while processing flow, the flow will be sent to the DLQ", exception)
        StateAndEventProcessor.Response(
            updatedState = null,
            responseEvents = listOf(),
            markForDLQ = true
        )
    }

    override fun process(exception: FlowTransientException): StateAndEventProcessor.Response<Checkpoint> = withEscalation {
        val context = exception.getFlowContext()
        val flowCheckpoint = context.checkpoint

        /** If we have reached the maximum number of retries then we escalate this to a fatal
         * exception and DLQ the flow
         */
        if (flowCheckpoint.currentRetryCount >= maxRetryAttempts) {
            process(
                FlowFatalException(
                    "Max retry attempts '${maxRetryAttempts}' has been reached.",
                    context,
                    exception
                )
            )
        }

        log.info(
            "A transient exception was thrown the event that failed will be retried. event='${context.inputEvent}'",
            exception
        )

        val records = createStatusRecord(exception.getFlowContext().checkpoint.flowId) {
            flowMessageFactory.createFlowRetryingStatusMessage(exception.getFlowContext().checkpoint)
        }

        // Set up records before the rollback, just in case a transient exception happens after a flow is initialised
        // but before the first checkpoint has been recorded.
        flowCheckpoint.rollback()
        flowCheckpoint.markForRetry(context.inputEvent, exception)

        flowEventContextConverter.convert(context.copy(outputRecords = context.outputRecords + records))
    }

    override fun process(exception: FlowFatalException): StateAndEventProcessor.Response<Checkpoint> = withEscalation {
        val msg = "Flow processing has failed due to a fatal exception, the flow will be moved to the DLQ"
        log.error(msg, exception)
        val records = createStatusRecord(exception.getFlowContext().checkpoint.flowId) {
            flowMessageFactory.createFlowFailedStatusMessage(
                exception.getFlowContext().checkpoint,
                FLOW_FAILED,
                msg
            )
        }

        StateAndEventProcessor.Response(
            updatedState = null,
            responseEvents = records,
            markForDLQ = true
        )
    }

    private fun createStatusRecord(id: String, statusGenerator: () -> FlowStatus) : List<Record<*, *>>{
        return try {
            val status = statusGenerator()
            listOf(flowRecordFactory.createFlowStatusRecord(status))
        } catch (e: IllegalStateException) {
            // Most errors should happen after a flow has been initialised. However, it is possible for
            // initialisation to have not yet happened at the point the failure is hit if it's a session init message
            // and something goes wrong in trying to retrieve the sandbox. In this case we cannot update the status
            // correctly. This shouldn't matter however - in this case we're treating the issue as the flow never
            // starting at all. We'll still log that the error was seen.
            log.warn("Could not create a flow status message for a failed flow with ID $id as " +
                    "the flow start context was missing.")
            listOf()
        }
    }

    override fun process(exception: FlowEventException): StateAndEventProcessor.Response<Checkpoint> = withEscalation {
        log.warn("A non critical error was reported while processing the event.", exception)
        flowEventContextConverter.convert(exception.getFlowContext())
    }

    override fun process(exception: FlowPlatformException): StateAndEventProcessor.Response<Checkpoint> = withEscalation {
        val context = exception.getFlowContext()
        val checkpoint = context.checkpoint

        /**
         * the exception message can't be null, we can remove the !! as
         * part of this ticket:
         * https://r3-cev.atlassian.net/browse/CORE-5170
         */
        checkpoint.setPendingPlatformError(PLATFORM_ERROR, exception.message!!)
        checkpoint.waitingFor = WaitingFor(net.corda.data.flow.state.waiting.Wakeup())

        val record = flowRecordFactory.createFlowEventRecord(checkpoint.flowId, Wakeup())
        flowEventContextConverter.convert(context.copy(outputRecords = context.outputRecords + record))
    }

    private fun withEscalation(handler: () -> StateAndEventProcessor.Response<Checkpoint>) : StateAndEventProcessor.Response<Checkpoint> {
        return try {
            handler()
        } catch(t: Throwable) {
            // The exception handler failed. Rather than take the whole pipeline down, forcibly DLQ the offending event.
            log.error("An unrecoverable failure occurred while trying to process a flow event: ${t.message}. Sending to the dead letter queue.", t)
            StateAndEventProcessor.Response(
                updatedState = null,
                responseEvents = listOf(),
                markForDLQ = true
            )
        }
    }

    private fun FlowProcessingException.getFlowContext(): FlowEventContext<*> {
        /** Hack: the !! is temporary , for now we are leaving the FlowProcessingException with an optional flow event
         *  context this can be changed once all the exception handling is implemented.
         *  https://r3-cev.atlassian.net/browse/CORE-5170
         */
        return flowEventContext!!
    }
}