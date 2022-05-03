package net.corda.flow.pipeline.handlers.waiting

import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowEventContext
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

@Component(service = [FlowWaitingForHandler::class])
class WakeupWaitingForHandler : FlowWaitingForHandler<Wakeup> {

    private companion object {
        val log = contextLogger()
    }

    override val type = Wakeup::class.java

    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: Wakeup): FlowContinuation {
        return if (context.inputEventPayload is net.corda.data.flow.event.Wakeup) {
            log.info("Waking up [${context.checkpoint.flowId}]")
            FlowContinuation.Run(Unit)
        } else {
            log.info(
                "Not waking up flow [${context.checkpoint.flowId}] as it received a ${context.inputEventPayload!!::class.qualifiedName} " +
                        "instead of a ${net.corda.data.flow.event.Wakeup::class.qualifiedName} event"
            )
            FlowContinuation.Continue
        }
    }
}