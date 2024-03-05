package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import org.osgi.service.component.annotations.Component

@Suppress("Unused")
@Component(service = [FlowRequestHandler::class])
class FlowRetryRequestHandler : FlowRequestHandler<FlowIORequest.FlowRetry> {

    override val type = FlowIORequest.FlowRetry::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.FlowRetry): WaitingFor? {
        return null
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.FlowRetry): FlowEventContext<Any> {
        throw Exception() // TODO
    }
}