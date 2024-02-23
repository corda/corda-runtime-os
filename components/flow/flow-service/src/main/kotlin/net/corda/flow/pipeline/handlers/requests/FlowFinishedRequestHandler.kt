package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.addTerminationKeyToMeta
import net.corda.flow.pipeline.handlers.requests.helper.getRecords
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("Unused")
@Component(service = [FlowRequestHandler::class])
class FlowFinishedRequestHandler @Activate constructor(
    @Reference(service = FlowMessageFactory::class)
    private val flowMessageFactory: FlowMessageFactory,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowRequestHandler<FlowIORequest.FlowFinished> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val type = FlowIORequest.FlowFinished::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.FlowFinished): WaitingFor? {
        return null
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.FlowFinished
    ): FlowEventContext<Any> {
        val checkpoint = context.checkpoint

        val status = flowMessageFactory.createFlowCompleteStatusMessage(checkpoint, request.result)
        val records = getRecords(flowRecordFactory, context, status)

        if (log.isTraceEnabled)
            log.trace("Flow [${checkpoint.flowId}] completed successfully")
        checkpoint.markDeleted()

        context.flowMetrics.flowCompletedSuccessfully()
        val metaDataWithTermination = addTerminationKeyToMeta(context.metadata)
        return context.copy(outputRecords = context.outputRecords + records, metadata = metaDataWithTermination)
    }
}
