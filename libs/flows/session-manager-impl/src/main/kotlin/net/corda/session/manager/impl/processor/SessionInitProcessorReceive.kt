package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateAckEvent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.uncheckedCast
import java.time.Instant
import java.util.*

/**
 * Process SessionInit messages.
 * Generate [SessionAck] for the SessionInit and create a new [SessionState]
 * If [SessionState] for the given sessionId is null log the duplicate event.
 */
class SessionInitProcessorReceive(
    private val key: Any,
    private val sessionState: SessionState?,
    private val sessionEvent: SessionEvent,
    private val instant: Instant
) : SessionEventProcessor {

    private companion object {
        private val logger = contextLogger()
    }

    override fun execute(): SessionState {
        if (sessionState != null) {
            logger.debug { "Received duplicate SessionInit on key $key for session which was not null: $sessionState" }
            sessionState.apply {
                sendEventsState.undeliveredMessages =
                    sendEventsState.undeliveredMessages.plus(generateAckEvent(sessionEvent.sequenceNum, sessionId, instant))

            }
            return sessionState
        }

        val sessionInit: SessionInit = uncheckedCast(sessionEvent.payload)
        val sessionId = sessionEvent.sessionId
        val seqNum = sessionEvent.sequenceNum
        val newSessionState = SessionState.newBuilder()
            .setSessionId(sessionId)
            .setSessionStartTime(instant.toEpochMilli())
            .setIsInitiator(false)
            .setCounterpartyIdentity(sessionInit.initiatingIdentity)
            .setReceivedEventsState(SessionProcessState(seqNum, mutableListOf(sessionEvent)))
            .setSendEventsState(SessionProcessState(seqNum - 1, mutableListOf(generateAckEvent(seqNum, sessionId, instant))))
            .setStatus(SessionStateType.CONFIRMED)
            .build()

        logger.debug { "Created new session with id $sessionId for SessionInit received on key $key. sessionState $newSessionState"}

        return newSessionState
    }
}
