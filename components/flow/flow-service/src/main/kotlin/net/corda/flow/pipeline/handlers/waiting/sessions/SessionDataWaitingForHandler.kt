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

            val erroredSessions = flowSessionManager.getSessionsWithStatus(
                checkpoint,
                waitingFor.sessionIds,
                SessionStateType.ERROR
            ).map { it.sessionId }

            when {
                erroredSessions.isNotEmpty() -> {
                    if (haveAllSessionsErroredOrReceivedData(waitingFor, erroredSessions, receivedSessionDataEvents)) {
                        flowSessionManager.acknowledgeReceivedEvents(receivedSessionDataEvents)
                        FlowContinuation.Error(CordaRuntimeException("Failed to receive due to errors from sessions: $erroredSessions"))
                    } else {
                        FlowContinuation.Continue
                    }
                }
                receivedSessionDataEvents.size == waitingFor.sessionIds.size -> {
                    try {
                        val payloads = convertToIncomingPayloads(receivedSessionDataEvents)
                        flowSessionManager.acknowledgeReceivedEvents(receivedSessionDataEvents)
                        FlowContinuation.Run(payloads)
                    } catch (e: IllegalStateException) {
                        FlowContinuation.Error(e)
                    }
                }
                else -> FlowContinuation.Continue
            }
        } catch (e: FlowSessionMissingException) {
            // TODO CORE-4850 Wakeup with error when session does not exist
            throw FlowFatalException(e.message, context, e)
        }
    }

    private fun haveAllSessionsErroredOrReceivedData(
        waitingFor: SessionData,
        erroredSessions: List<String>,
        receivedDataEvents: List<Pair<SessionState, SessionEvent>>
    ): Boolean {
        val receivedEventSessionIds = receivedDataEvents.map { (sessionState, _) -> sessionState.sessionId }
        return waitingFor.sessionIds.toSet() == (receivedEventSessionIds + erroredSessions).toSet()
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