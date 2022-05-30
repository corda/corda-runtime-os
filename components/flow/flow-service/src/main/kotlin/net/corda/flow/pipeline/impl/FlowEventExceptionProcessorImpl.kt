package net.corda.flow.pipeline.impl

import net.corda.data.flow.state.Checkpoint
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowEventExceptionProcessor
import net.corda.flow.pipeline.converters.FlowEventContextConverter
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowProcessingException
import net.corda.flow.pipeline.exceptions.FlowProcessingExceptionTypes.FLOW_FAILED
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
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

    override fun process(exception: Exception): StateAndEventProcessor.Response<Checkpoint> {
        log.error("Unexpected exception while processing flow, the flow will be sent to the DLQ", exception)
        return StateAndEventProcessor.Response(
            updatedState = null,
            responseEvents = listOf(),
            markForDLQ = true
        )
    }

    override fun process(exception: FlowTransientException): StateAndEventProcessor.Response<Checkpoint> {
        val context = exception.getFlowContext()
        val flowCheckpoint = context.checkpoint

        /** If we have reached the maximum number of retries then we escalate this to a fatal
         * exception and DLQ the flow
         */
        if (flowCheckpoint.currentRetryCount >= maxRetryAttempts) {
            return process(
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

        flowCheckpoint.rollback()
        flowCheckpoint.markForRetry(context.inputEvent, exception)

        val status = flowMessageFactory.createFlowRetryingStatusMessage(exception.getFlowContext().checkpoint)
        val records = listOf(
            flowRecordFactory.createFlowStatusRecord(status)
        )

        return flowEventContextConverter.convert(context.copy(outputRecords = context.outputRecords + records))
    }

    override fun process(exception: FlowFatalException): StateAndEventProcessor.Response<Checkpoint> {
        val msg = "Flow processing has failed due to a fatal exception, the flow will be moved to the DLQ"
        log.error(msg, exception)
        val status = flowMessageFactory.createFlowFailedStatusMessage(
            exception.getFlowContext().checkpoint,
            FLOW_FAILED,
            msg
        )

        val records = listOf(
            flowRecordFactory.createFlowStatusRecord(status)
        )

        return StateAndEventProcessor.Response(
            updatedState = null,
            responseEvents = records,
            markForDLQ = true
        )
    }

    override fun process(exception: FlowEventException): StateAndEventProcessor.Response<Checkpoint> {
        log.warn("A non critical error was reported while processing the event.", exception)
        return flowEventContextConverter.convert(exception.getFlowContext())
    }

    private fun FlowProcessingException.getFlowContext(): FlowEventContext<*> {
        /** Hack: the !! is temporary , for now we are leaving the FlowProcessingException with an optional flow event
         *  context this can be changed once all the exception handling is implemented.
         */
        return flowEventContext!!
    }
}