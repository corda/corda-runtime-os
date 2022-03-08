package net.corda.flow.manager.impl.handlers.requests

import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.flow.manager.impl.FlowEventContext
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
        val checkpoint = requireCheckpoint(context)

        log.info("Sub-flow [${checkpoint.flowKey.flowId}] failed", request.exception)
        /*
         *  TODOs: Once the session management logic is implemented, we need to add logic here
         * to access the flow stack item to determine if any session clean up is required.
         */
        return context.copy(checkpoint = null)
    }
}