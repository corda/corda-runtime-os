package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.factory.SessionEventFactory
import net.corda.flow.pipeline.handlers.getInitiatingAndInitiatedParties
import net.corda.flow.pipeline.handlers.getSession
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.session.manager.SessionManager
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowRequestHandler::class])
class CloseSessionsRequestHandler @Activate constructor(
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager,
    @Reference(service = SessionEventFactory::class)
    private val sessionEventFactory: SessionEventFactory,
) : FlowRequestHandler<FlowIORequest.CloseSessions> {

    override val type = FlowIORequest.CloseSessions::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.CloseSessions): WaitingFor {
        return WaitingFor(SessionConfirmation(request.sessions.toList(), SessionConfirmationType.CLOSE))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.CloseSessions): FlowEventContext<Any> {
        val checkpoint = context.checkpoint

        val now = Instant.now()

        for (sessionId in request.sessions) {
            val sessionState = checkpoint.getSessionState(sessionId)
            val (initiatingIdentity, initiatedIdentity) = getInitiatingAndInitiatedParties(
                sessionState, checkpoint.flowKey.identity
            )
            val updatedSessionState = sessionManager.processMessageToSend(
                key = checkpoint.flowId,
                sessionState = checkpoint.getSessionState(sessionId),
                event = sessionEventFactory.create(sessionId,now,SessionClose()),
                instant = now
            )

            checkpoint.putSessionState(updatedSessionState)
        }

        return context
    }
}