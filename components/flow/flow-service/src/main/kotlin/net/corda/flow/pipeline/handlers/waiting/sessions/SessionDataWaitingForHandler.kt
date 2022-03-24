package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.SessionData
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.pipeline.handlers.waiting.requireCheckpoint
import net.corda.session.manager.SessionManager
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowWaitingForHandler::class])
class SessionDataWaitingForHandler @Activate constructor(
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager
) : FlowWaitingForHandler<SessionData> {

    override val type = SessionData::class.java

    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: SessionData): FlowContinuation {
        val checkpoint = requireCheckpoint(context)
        val receivedEvents = sessionManager.getReceivedEvents(checkpoint, waitingFor.sessionIds)
        return if (receivedEvents.size != waitingFor.sessionIds.size) {
            FlowContinuation.Continue
        } else {
            sessionManager.acknowledgeReceivedEvents(receivedEvents)
            FlowContinuation.Run(convertToIncomingPayloads(receivedEvents))
        }
    }

    private fun convertToIncomingPayloads(receivedEvents: List<Pair<SessionState, SessionEvent>>): Map<String, Any> {
        return receivedEvents.associate { (_, event) ->
            when (val sessionPayload = event.payload) {
                is net.corda.data.flow.event.session.SessionData -> Pair(event.sessionId, sessionPayload.payload.array())
                else -> throw FlowProcessingException(
                    "Received events should be data messages but got a ${sessionPayload::class.java.name} instead"
                )
            }
        }
    }
}