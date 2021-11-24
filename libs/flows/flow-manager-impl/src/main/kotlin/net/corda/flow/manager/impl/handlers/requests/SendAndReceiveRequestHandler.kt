package net.corda.flow.manager.impl.handlers.requests

import net.corda.data.flow.request.SendAndReceiveRequest
import net.corda.flow.manager.FlowEventContext
import net.corda.flow.manager.impl.handlers.requests.FlowRequestHandler
import net.corda.flow.manager.impl.handlers.requests.setCheckpointFlowIORequest
import net.corda.flow.statemachine.requests.FlowIORequest
import org.osgi.service.component.annotations.Component

@Component(service = [FlowRequestHandler::class])
class SendAndReceiveRequestHandler : FlowRequestHandler<FlowIORequest.SendAndReceive> {

    override val type = FlowIORequest.SendAndReceive::class.java

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.SendAndReceive): FlowEventContext<Any> {
        context.setCheckpointFlowIORequest(SendAndReceiveRequest())
        TODO("Not yet implemented")
    }
}