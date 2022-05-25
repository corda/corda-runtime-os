package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionData
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.FlowSessionMissingException
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowWaitingForHandler::class])
class SessionDataWaitingForHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager
) : FlowWaitingForHandler<SessionData> {

    override val type = SessionData::class.java

    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: SessionData): FlowContinuation {
        val checkpoint = context.checkpoint

        return try {
            val receivedSessionDataEvents = flowSessionManager.getReceivedEvents(checkpoint, waitingFor.sessionIds)
            val receivedSessions = receivedSessionDataEvents.map { (session, _) -> session.sessionId }

            val erroredSessions = flowSessionManager.getSessionsWithStatus(
                checkpoint,
                waitingFor.sessionIds - receivedSessions,
                SessionStateType.ERROR
            ).map { it.sessionId }

            val closingSessions = flowSessionManager.getSessionsWithStatus(
                checkpoint,
                waitingFor.sessionIds - receivedSessions,
                SessionStateType.CLOSING
            ).map { it.sessionId }

            when {
                receivedSessionDataEvents.size == waitingFor.sessionIds.size -> {
                    try {
                        val payloads = convertToIncomingPayloads(receivedSessionDataEvents)
                        flowSessionManager.acknowledgeReceivedEvents(receivedSessionDataEvents)
                        FlowContinuation.Run(payloads)
                    } catch (e: IllegalStateException) {
                        FlowContinuation.Error(e)
                    }
                }
                erroredSessions.isNotEmpty() -> {
                    resumeWithErrorIfAllSessionsReceivedEvents(
                        waitingFor,
                        erroredSessions,
                        closingSessions,
                        receivedSessionDataEvents,
                        message = "Failed to receive due to receiving errors from sessions: $erroredSessions"
                    )
                }
                closingSessions.isNotEmpty() -> {
                    resumeWithErrorIfAllSessionsReceivedEvents(
                        waitingFor,
                        erroredSessions,
                        closingSessions,
                        receivedSessionDataEvents,
                        message = "Failed to receive due to receiving closes from sessions: $closingSessions"
                    )
                }
                else -> FlowContinuation.Continue
            }
        } catch (e: FlowSessionMissingException) {
            // TODO CORE-4850 Wakeup with error when session does not exist
            throw FlowFatalException(e.message, context, e)
        }
    }

    private fun resumeWithErrorIfAllSessionsReceivedEvents(
        waitingFor: SessionData,
        erroredSessions: List<String>,
        closingSessions: List<String>,
        receivedSessionDataEvents: List<Pair<SessionState, SessionEvent>>,
        message: String
    ): FlowContinuation {
        return if (haveAllSessionsReceivedEvents(waitingFor, erroredSessions, closingSessions, receivedSessionDataEvents)) {
            flowSessionManager.acknowledgeReceivedEvents(receivedSessionDataEvents)
            FlowContinuation.Error(CordaRuntimeException(message))
        } else {
            FlowContinuation.Continue
        }
    }

    private fun haveAllSessionsReceivedEvents(
        waitingFor: SessionData,
        erroredSessions: List<String>,
        closingSessions: List<String>,
        receivedSessionDataEvents: List<Pair<SessionState, SessionEvent>>
    ): Boolean {
        val receivedEventSessionIds = receivedSessionDataEvents.map { (sessionState, _) -> sessionState.sessionId }
        return waitingFor.sessionIds.toSet() == (receivedEventSessionIds + erroredSessions + closingSessions).toSet()
    }

    private fun convertToIncomingPayloads(receivedEvents: List<Pair<SessionState, SessionEvent>>): Map<String, ByteArray> {
        return receivedEvents.associate { (_, event) ->
            when (val sessionPayload = event.payload) {
                is net.corda.data.flow.event.session.SessionData -> Pair(event.sessionId, sessionPayload.payload.array())
                else -> throw IllegalStateException(
                    "Received events should be data messages but got a ${sessionPayload::class.java.name} instead"
                )
            }
        }
    }
}