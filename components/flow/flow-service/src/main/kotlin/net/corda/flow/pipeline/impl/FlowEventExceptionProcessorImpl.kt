package net.corda.flow.pipeline.impl

import net.corda.data.flow.event.StartFlow
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowEventExceptionProcessor
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowProcessingException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.CancellationException

@Suppress("CanBePrimaryConstructorProperty", "Unused")
@Component(service = [FlowEventExceptionProcessor::class])
class FlowEventExceptionProcessorImpl @Activate constructor(
    @Reference(service = FlowMessageFactory::class)
    private val flowMessageFactory: FlowMessageFactory,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowEventExceptionProcessor {

    private companion object {
        val log = contextLogger()
    }

    private var maxRetryAttempts = 0

    override fun configure(config: SmartConfig) {
        maxRetryAttempts = config.getInt(PROCESSING_MAX_RETRY_ATTEMPTS)
    }

    override fun process(exception: Exception): FlowEventContext<Any> {
        log.error("Flow processing has failed due to an unexpected exception, the flow will be moved to the DLQ", exception)

        // we need to throw a CancellationException to signal the flow should be moved to the DLQ
        // this should change with CORE-4753
        throw CancellationException()
    }

    override fun process(exception: FlowTransientException): FlowEventContext<Any> {
        val context = exception.getFlowContext()
        val flowCheckpoint = context.checkpoint

        // If we have reached the maximum number of retries then we escalate this to a fatal
        // exception and DLQ the flow
        if (flowCheckpoint.currentRetryCount >= maxRetryAttempts) {
            return process(
                FlowFatalException(
                    "Max retry attempts '${maxRetryAttempts}' has been reached.",
                    context,
                    exception
                )
            )
        }

        flowCheckpoint.rollback()
        flowCheckpoint.markForRetry(context.inputEvent, exception)

        // Event specific exception handling
        return when(context.inputEventPayload){
            is StartFlow -> startFlowEventTransientException(exception)
            else -> context
        }
    }

    override fun process(exception: FlowFatalException): FlowEventContext<Any> {
        log.error("Flow processing has failed due to a fatal exception, the flow will be moved to the DLQ", exception)

        // we need to throw a CancellationException to signal the flow should be moved to the DLQ
        // this should change with CORE-4753
        throw CancellationException()
    }

    override fun process(exception: FlowEventException): FlowEventContext<Any> {
        log.warn("A non critical error was reported while processing the event.", exception)
        return exception.getFlowContext()
    }

    private fun FlowProcessingException.getFlowContext(): FlowEventContext<Any> {
        // Hack: the !! is temporary , for now we are leaving the FlowProcessingException
        // with an optional flow event context this can be changed once all the exception handling is implemented.
        return flowEventContext!!
    }

    private fun startFlowEventTransientException(exception: FlowProcessingException): FlowEventContext<Any> {
        // If we get a transient error while trying to start a flow then we
        // need to update the flow's status to 'Retrying'
        val context = exception.getFlowContext()
        val status = flowMessageFactory.createFlowRetryingStatusMessage(exception.getFlowContext().checkpoint)
        val records = listOf(
            flowRecordFactory.createFlowStatusRecord(status)
        )

        return context.copy(outputRecords = context.outputRecords + records)
    }
}