package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.Wakeup
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory

@Component(service = [FlowEventHandler::class])
class WakeupEventHandler : FlowEventHandler<Wakeup> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val type = Wakeup::class.java

    override fun preProcess(context: FlowEventContext<Wakeup>): FlowEventContext<Wakeup> {
        return if (context.checkpoint.doesExist) {
            context
        } else {
            log.debug {
                "Received a ${Wakeup::class.simpleName} for flow [${context.inputEvent.flowId}] that does not exist. " +
                        "The event will be discarded."
            }
            throw FlowEventException(
                "WakeupEventHandler received a ${Wakeup::class.simpleName} for flow [${context.inputEvent.flowId}] that does not exist"
            )
        }
    }
}
