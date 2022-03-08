package net.corda.flow.manager.impl.handlers.requests

import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.flow.manager.impl.FlowEventContext
import org.osgi.service.component.annotations.Component

@Component(service = [FlowRequestHandler::class])
class WaitForSessionConfirmationsRequestHandler : FlowRequestHandler<FlowIORequest.WaitForSessionConfirmations> {

    override val type = FlowIORequest.WaitForSessionConfirmations::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.WaitForSessionConfirmations): WaitingFor {
        TODO("Not yet implemented")
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.WaitForSessionConfirmations): FlowEventContext<Any> {
        TODO("Not yet implemented")
    }
}