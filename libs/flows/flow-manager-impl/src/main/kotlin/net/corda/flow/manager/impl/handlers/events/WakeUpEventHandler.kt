package net.corda.flow.manager.impl.handlers.events

import net.corda.data.flow.event.Wakeup
import net.corda.flow.manager.impl.FlowEventContext
import org.osgi.service.component.annotations.Component

@Component(service = [FlowEventHandler::class])
class WakeUpEventHandler : FlowEventHandler<Wakeup> {

    override val type = Wakeup::class.java

    override fun preProcess(context: FlowEventContext<Wakeup>): FlowEventContext<Wakeup> {
        requireCheckpoint(context)
        return context
    }
}