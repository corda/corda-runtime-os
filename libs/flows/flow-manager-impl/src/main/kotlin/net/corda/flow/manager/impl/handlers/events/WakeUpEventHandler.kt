package net.corda.flow.manager.impl.handlers.events

import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.request.ForceCheckpointRequest
import net.corda.flow.manager.FlowEventContext
import net.corda.flow.manager.impl.handlers.events.FlowEventHandler
import net.corda.flow.manager.impl.handlers.events.requireCheckpoint
import net.corda.flow.statemachine.FlowContinuation
import org.osgi.service.component.annotations.Component

@Component(service = [FlowEventHandler::class])
class WakeUpEventHandler : FlowEventHandler<Wakeup> {

    override val type = Wakeup::class.java

    override fun preProcess(context: FlowEventContext<Wakeup>): FlowEventContext<Wakeup> {
        requireCheckpoint(context.checkpoint)
        return context
    }

    override fun resumeOrContinue(context: FlowEventContext<Wakeup>): FlowContinuation {
        val checkpoint = requireCheckpoint(context.checkpoint)
        return if (checkpoint.flowState.flowIORequest.request is ForceCheckpointRequest) {
            FlowContinuation.Run(Unit)
        } else {
            FlowContinuation.Continue
        }
    }

    override fun postProcess(context: FlowEventContext<Wakeup>): FlowEventContext<Wakeup> {
        return context
    }
}