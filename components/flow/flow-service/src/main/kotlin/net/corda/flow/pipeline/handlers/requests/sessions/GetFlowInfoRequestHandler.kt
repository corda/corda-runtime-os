package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
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