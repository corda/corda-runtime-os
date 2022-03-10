package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.Wakeup
import net.corda.flow.pipeline.FlowEventContext
import org.osgi.service.component.annotations.Component

@Component(service = [FlowEventHandler::class])
class WakeUpEventHandler : FlowEventHandler<Wakeup> {

    override val type = Wakeup::class.java

    override fun preProcess(context: FlowEventContext<Wakeup>): FlowEventContext<Wakeup> {
        requireCheckpoint(context)
        return context
    }
}