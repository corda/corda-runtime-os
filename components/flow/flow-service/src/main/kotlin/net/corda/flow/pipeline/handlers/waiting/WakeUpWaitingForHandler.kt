package net.corda.flow.pipeline.handlers.waiting

import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowEventContext
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