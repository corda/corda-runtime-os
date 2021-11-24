package net.corda.flow.manager.impl.handlers.requests

import net.corda.data.flow.request.WaitForSessionConfirmationsRequest
import net.corda.flow.manager.FlowEventContext
import net.corda.flow.manager.impl.handlers.requests.FlowRequestHandler
import net.corda.flow.manager.impl.handlers.requests.setCheckpointFlowIORequest
import net.corda.flow.statemachine.requests.FlowIORequest
import org.osgi.service.component.annotations.Component

@Component(service = [FlowRequestHandler::class])
class WaitForSessionConfirmationsRequestHandler : FlowRequestHandler<FlowIORequest.WaitForSessionConfirmations> {

    override val type = FlowIORequest.WaitForSessionConfirmations::class.java

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.WaitForSessionConfirmations): FlowEventContext<Any> {
        context.setCheckpointFlowIORequest(WaitForSessionConfirmationsRequest())
        TODO("Not yet implemented")
    }
}