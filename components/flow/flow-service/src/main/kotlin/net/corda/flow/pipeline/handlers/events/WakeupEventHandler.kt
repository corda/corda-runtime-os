package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.Wakeup
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

@Component(service = [FlowEventHandler::class])
class WakeupEventHandler : FlowEventHandler<Wakeup> {

    private companion object {
        val log = contextLogger()
    }

    override val type = Wakeup::class.java

    override fun preProcess(context: FlowEventContext<Wakeup>): FlowEventContext<Wakeup> {
        return if (context.checkpoint.doesExist) {
            context
        } else {
            log.info(
                "Received a ${Wakeup::class.simpleName} for flow [${context.inputEvent.flowId}] that does not exist. " +
                        "The event will be discarded."
            )
            throw FlowEventException(
                "Received a ${Wakeup::class.simpleName} for flow [${context.inputEvent.flowId}] that does not exist"
            )
        }
    }
}