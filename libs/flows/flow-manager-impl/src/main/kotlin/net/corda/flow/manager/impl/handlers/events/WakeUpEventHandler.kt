package net.corda.flow.manager.impl.handlers.events

import net.corda.data.flow.event.Wakeup
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.fiber.FlowContinuation
import org.osgi.service.component.annotations.Component
import net.corda.data.flow.state.waiting.Wakeup as WaitingForWakeup

@Component(service = [FlowEventHandler::class])
class WakeUpEventHandler : FlowEventHandler<Wakeup> {

    override val type = Wakeup::class.java

    override fun preProcess(context: FlowEventContext<Wakeup>): FlowEventContext<Wakeup> {
        requireCheckpoint(context.checkpoint)
        return context
    }

    override fun resumeOrContinue(context: FlowEventContext<Wakeup>): FlowContinuation {
        val checkpoint = requireCheckpoint(context.checkpoint)
        return if (checkpoint.flowState.waitingFor.value is WaitingForWakeup) {
            FlowContinuation.Run(Unit)
        } else {
            FlowContinuation.Continue
        }
    }

    override fun postProcess(context: FlowEventContext<Wakeup>): FlowEventContext<Wakeup> {
        return context
    }
}