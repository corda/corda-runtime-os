package net.corda.flow.manager.impl.handlers.requests

import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.fiber.requests.FlowIORequest
import org.osgi.service.component.annotations.Component

@Component(service = [FlowRequestHandler::class])
class SleepRequestHandler : FlowRequestHandler<FlowIORequest.Sleep> {

    override val type = FlowIORequest.Sleep::class.java

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.Sleep): FlowEventContext<Any> {
        TODO("Not yet implemented")
    }
}