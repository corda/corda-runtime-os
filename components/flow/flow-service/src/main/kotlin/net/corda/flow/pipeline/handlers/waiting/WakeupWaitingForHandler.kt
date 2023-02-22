package net.corda.flow.pipeline.handlers.waiting

import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Component

@Component(service = [FlowWaitingForHandler::class])
class WakeupWaitingForHandler : FlowWaitingForHandler<Wakeup> {

    override val type = Wakeup::class.java

    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: Wakeup): FlowContinuation {
        val pendingPlatformError = context.checkpoint.pendingPlatformError
        return if (pendingPlatformError != null) {
            FlowContinuation.Error(
                CordaRuntimeException(
                    "Type='${pendingPlatformError.errorType}' Message='${pendingPlatformError.errorMessage}'"
                )
            )
        } else {
            FlowContinuation.Run(Unit)
        }
    }
}
