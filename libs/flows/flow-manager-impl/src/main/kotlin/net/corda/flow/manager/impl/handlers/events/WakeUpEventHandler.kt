package net.corda.flow.manager.impl.handlers.events

import net.corda.data.flow.event.Wakeup
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.manager.fiber.FlowContinuation
import org.osgi.service.component.annotations.Component
import net.corda.data.flow.state.waiting.Wakeup as WaitingForWakeup

@Component(service = [FlowEventHandler::class])
class WakeUpEventHandler : FlowEventHandler<Wakeup> {

    override val type = Wakeup::class.java

    override fun preProcess(context: FlowEventContext<Wakeup>): FlowEventContext<Wakeup> {
        requireCheckpoint(context)
        return context
    }

    override fun runOrContinue(context: FlowEventContext<Wakeup>): FlowContinuation {
        val checkpoint = requireCheckpoint(context)
        return if (checkpoint.flowState.requireWaitingFor().value is WaitingForWakeup) {
            FlowContinuation.Run(Unit)
        } else {
            FlowContinuation.Continue
        }
    }

    override fun postProcess(context: FlowEventContext<Wakeup>): FlowEventContext<Wakeup> {
        return context
    }
}