package net.corda.flow.pipeline.handlers.waiting

import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowEventContext
import org.osgi.service.component.annotations.Component

object WaitingForStartFlow

@Component(service = [FlowWaitingForHandler::class])
class StartRPCFlowWaitingForHandler : FlowWaitingForHandler<WaitingForStartFlow> {

    override val type = WaitingForStartFlow::class.java

    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: WaitingForStartFlow): FlowContinuation {
        return FlowContinuation.Run(Unit)
    }
}