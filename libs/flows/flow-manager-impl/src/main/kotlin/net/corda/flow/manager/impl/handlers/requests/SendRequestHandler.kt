package net.corda.flow.manager.impl.handlers.requests

import net.corda.data.flow.request.SendRequest
import net.corda.flow.manager.FlowEventContext
import net.corda.flow.manager.impl.handlers.requests.FlowRequestHandler
import net.corda.flow.manager.impl.handlers.requests.setCheckpointFlowIORequest
import net.corda.flow.statemachine.requests.FlowIORequest
import org.osgi.service.component.annotations.Component

@Component(service = [FlowRequestHandler::class])
class SendRequestHandler : FlowRequestHandler<FlowIORequest.Send> {

    override val type = FlowIORequest.Send::class.java

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.Send): FlowEventContext<Any> {
        context.setCheckpointFlowIORequest(SendRequest())
        TODO("Not yet implemented")
    }
}