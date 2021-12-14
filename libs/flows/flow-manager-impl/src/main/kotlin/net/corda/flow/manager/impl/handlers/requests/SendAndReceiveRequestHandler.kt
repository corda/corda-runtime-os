package net.corda.flow.manager.impl.handlers.requests

import net.corda.flow.manager.FlowEventContext
import net.corda.flow.statemachine.requests.FlowIORequest
import org.osgi.service.component.annotations.Component

@Component(service = [FlowRequestHandler::class])
class SendAndReceiveRequestHandler : FlowRequestHandler<FlowIORequest.SendAndReceive> {

    override val type = FlowIORequest.SendAndReceive::class.java

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.SendAndReceive): FlowEventContext<Any> {
        TODO("Not yet implemented")
    }
}