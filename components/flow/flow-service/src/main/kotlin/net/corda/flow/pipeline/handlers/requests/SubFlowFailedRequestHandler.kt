package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

@Component(service = [FlowRequestHandler::class])
class SubFlowFailedRequestHandler : FlowRequestHandler<FlowIORequest.SubFlowFailed> {

    private companion object {
        val log = contextLogger()
    }

    override val type = FlowIORequest.SubFlowFailed::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.SubFlowFailed): WaitingFor {
        return WaitingFor(null)
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.SubFlowFailed
    ): FlowEventContext<Any> {

        log.info("Sub-flow [${context.checkpoint.flowId}] failed", request.exception)
        /*
         *  TODOs: Once the session management logic is implemented, we need to add logic here
         * to access the flow stack item to determine if any session clean up is required.
         */
        context.checkpoint.markDeleted()
        return context
    }
}