package net.corda.flow.manager.impl.handlers.requests

import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.flow.manager.impl.FlowEventContext
import org.osgi.service.component.annotations.Component

@Component(service = [FlowRequestHandler::class])
class GetFlowInfoRequestHandler : FlowRequestHandler<FlowIORequest.GetFlowInfo> {

    override val type = FlowIORequest.GetFlowInfo::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.GetFlowInfo): WaitingFor {
        TODO("Not yet implemented")
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.GetFlowInfo): FlowEventContext<Any> {
        TODO("Not yet implemented")
    }
}