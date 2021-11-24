package net.corda.flow.manager.impl.handlers.requests

import net.corda.data.flow.request.GetFlowInfoRequest
import net.corda.flow.manager.FlowEventContext
import net.corda.flow.manager.impl.handlers.requests.FlowRequestHandler
import net.corda.flow.manager.impl.handlers.requests.setCheckpointFlowIORequest
import net.corda.flow.statemachine.requests.FlowIORequest
import org.osgi.service.component.annotations.Component

@Component(service = [FlowRequestHandler::class])
class GetFlowInfoRequestHandler : FlowRequestHandler<FlowIORequest.GetFlowInfo> {

    override val type = FlowIORequest.GetFlowInfo::class.java

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.GetFlowInfo): FlowEventContext<Any> {
        context.setCheckpointFlowIORequest(GetFlowInfoRequest())
        TODO("Not yet implemented")
    }
}