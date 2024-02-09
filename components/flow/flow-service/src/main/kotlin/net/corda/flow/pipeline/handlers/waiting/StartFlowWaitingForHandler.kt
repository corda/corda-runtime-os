package net.corda.flow.pipeline.handlers.waiting

import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.events.FlowEventContext
import org.osgi.service.component.annotations.Component
import net.corda.data.flow.state.waiting.WaitingForStartFlow

@Component(service = [FlowWaitingForHandler::class])
class StartFlowWaitingForHandler : FlowWaitingForHandler<WaitingForStartFlow> {

    override val type = WaitingForStartFlow::class.java

    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: WaitingForStartFlow): FlowContinuation {
        return FlowContinuation.Run(Unit)
    }
}
