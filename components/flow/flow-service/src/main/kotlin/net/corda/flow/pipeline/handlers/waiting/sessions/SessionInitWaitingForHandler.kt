package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.handlers.getSession
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.session.manager.SessionManager
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

data class WaitingForSessionInit(val sessionId: String)

@Component(service = [FlowWaitingForHandler::class])
class SessionInitWaitingForHandler @Activate constructor(
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager
) : FlowWaitingForHandler<WaitingForSessionInit> {

    override val type = WaitingForSessionInit::class.java

    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: WaitingForSessionInit): FlowContinuation {
        val checkpoint = context.checkpoint!!
        val sessionState = checkpoint.getSession(waitingFor.sessionId) ?: throw FlowProcessingException("Session doesn't exist")
        val eventToAcknowledgeProcessingOf =
            sessionManager.getNextReceivedEvent(sessionState) ?: throw FlowProcessingException("No event to acknowledge")
        sessionManager.acknowledgeReceivedEvent(sessionState, eventToAcknowledgeProcessingOf.sequenceNum)

        return FlowContinuation.Run(Unit)
    }
}