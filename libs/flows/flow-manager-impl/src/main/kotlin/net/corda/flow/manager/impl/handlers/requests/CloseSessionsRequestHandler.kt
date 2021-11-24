package net.corda.flow.manager.impl.handlers.requests

import net.corda.data.flow.request.CloseSessionsRequest
import net.corda.flow.manager.FlowEventContext
import net.corda.flow.manager.impl.handlers.requests.FlowRequestHandler
import net.corda.flow.manager.impl.handlers.requests.setCheckpointFlowIORequest
import net.corda.flow.statemachine.requests.FlowIORequest
import org.osgi.service.component.annotations.Component

@Component(service = [FlowRequestHandler::class])
class CloseSessionsRequestHandler : FlowRequestHandler<FlowIORequest.CloseSessions> {

    override val type = FlowIORequest.CloseSessions::class.java

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.CloseSessions): FlowEventContext<Any> {
        context.setCheckpointFlowIORequest(CloseSessionsRequest())
        TODO("Not yet implemented")
    }
}