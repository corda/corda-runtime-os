package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.addOrReplaceSession
import net.corda.flow.pipeline.handlers.getSession
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.requests.requireCheckpoint
import net.corda.session.manager.SessionManager
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowRequestHandler::class])
class CloseSessionsRequestHandler @Activate constructor(
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager
) : FlowRequestHandler<FlowIORequest.CloseSessions> {

    override val type = FlowIORequest.CloseSessions::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.CloseSessions): WaitingFor {
        return WaitingFor(SessionConfirmation(request.sessions.toList(), SessionConfirmationType.CLOSE))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.CloseSessions): FlowEventContext<Any> {
        val checkpoint = requireCheckpoint(context)

        val now = Instant.now()

        for (sessionId in request.sessions) {

            val updatedSessionState = sessionManager.processMessageToSend(
                key = checkpoint.flowKey.flowId,
                sessionState = checkpoint.getSession(sessionId),
                event = SessionEvent.newBuilder()
                    .setSessionId(sessionId)
                    .setMessageDirection(MessageDirection.OUTBOUND)
                    .setTimestamp(now)
                    .setSequenceNum(null)
                    .setPayload(SessionClose())
                    .build(),
                instant = now
            )

            checkpoint.addOrReplaceSession(updatedSessionState)
        }

        return context
    }
}