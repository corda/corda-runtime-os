package net.corda.flow.manager.impl.handlers.status

import net.corda.flow.manager.fiber.FlowContinuation
import net.corda.flow.manager.impl.FlowEventContext
import org.osgi.service.component.annotations.Component

object WaitingForStartFlow

@Component(service = [FlowWaitingForHandler::class])
class StartRPCFlowWaitingForHandler : FlowWaitingForHandler<WaitingForStartFlow> {

    override val type = WaitingForStartFlow::class.java

    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: WaitingForStartFlow): FlowContinuation {
        return FlowContinuation.Run(Unit)
    }
}