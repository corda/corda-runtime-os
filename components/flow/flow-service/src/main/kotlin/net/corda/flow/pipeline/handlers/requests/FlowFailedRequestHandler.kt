package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowProcessingExceptionTypes.FLOW_FAILED
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("Unused")
@Component(service = [FlowRequestHandler::class])
class FlowFailedRequestHandler @Activate constructor(
    @Reference(service = FlowMessageFactory::class)
    private val flowMessageFactory: FlowMessageFactory,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowRequestHandler<FlowIORequest.FlowFailed> {

    private companion object {
        val log = contextLogger()
    }

    override val type = FlowIORequest.FlowFailed::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.FlowFailed): WaitingFor {
        return WaitingFor(null)
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.FlowFailed): FlowEventContext<Any> {

        log.info("Flow [${context.checkpoint.flowId}] failed", request.exception)

        val status = flowMessageFactory.createFlowFailedStatusMessage(
            context.checkpoint,
            FLOW_FAILED,
            request.exception.message ?: request.exception.javaClass.name
        )

        val record = flowRecordFactory.createFlowStatusRecord(status)

        context.checkpoint.markDeleted()
        return context.copy(outputRecords = context.outputRecords + record)
    }
}