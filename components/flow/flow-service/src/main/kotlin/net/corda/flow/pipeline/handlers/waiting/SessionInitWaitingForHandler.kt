package net.corda.flow.pipeline.handlers.waiting

import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.runner.impl.FlowRunnerImpl
import net.corda.flow.pipeline.events.FlowEventContext
import org.osgi.service.component.annotations.Component

data class WaitingForSessionInit(val sessionId: String)

/**
 * This handler is only executed on initialization of the checkpoint
 * The Flow should be run immediately upon initialization of the checkpoint.
 * (See [FlowRunnerImpl])
 */
@Component(service = [FlowWaitingForHandler::class])
class SessionInitWaitingForHandler : FlowWaitingForHandler<WaitingForSessionInit> {
    override val type = WaitingForSessionInit::class.java

    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: WaitingForSessionInit): FlowContinuation {
        return FlowContinuation.Run(Unit)
    }
}
