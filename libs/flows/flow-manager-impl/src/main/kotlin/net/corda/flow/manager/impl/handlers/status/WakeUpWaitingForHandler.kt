package net.corda.flow.manager.impl.handlers.status

import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.manager.fiber.FlowContinuation
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

@Component(service = [FlowWaitingForHandler::class])
class WakeUpWaitingForHandler : FlowWaitingForHandler<Wakeup> {

    private companion object {
        val log = contextLogger()
    }

    override val type = Wakeup::class.java

    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: Wakeup): FlowContinuation {
        log.info("Waking up [${context.checkpoint!!.flowKey.flowId}]")
        return FlowContinuation.Run(Unit)
    }
}