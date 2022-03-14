package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.uncheckedCast
import java.time.Instant

/**
 * Process SessionInit messages to be sent to a counterparty.
 * Create a new [SessionState]
 * If [SessionState] for the given sessionId is null log the duplicate event.
 */
class SessionInitProcessorSend(
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
            logger.error("Tried to send SessionInit on key $key for session which was not null: $sessionState")
            return sessionState.apply {
                status = SessionStateType.ERROR
                sendEventsState.undeliveredMessages = sendEventsState.undeliveredMessages.plus(
                    generateErrorEvent(sessionState,
                        "Tried to send SessionInit for session which is not null",
                        "SessionInit-SessionMismatch", instant
                    )
                )
            }
        }

        val sessionInit: SessionInit = uncheckedCast(sessionEvent.payload)
        val newSessionId = sessionEvent.sessionId
        val seqNum = 1

        sessionEvent.apply {
            sequenceNum = seqNum
            timestamp = instant
        }

        val newSessionState = SessionState.newBuilder()
            .setSessionId(newSessionId)
            .setSessionStartTime(instant)
            .setLastReceivedMessageTime(instant)
            .setLastSentMessageTime(instant)
            .setIsInitiator(true)
            .setSendAck(false)
            .setCounterpartyIdentity(sessionInit.initiatedIdentity)
            .setReceivedEventsState(SessionProcessState(0, mutableListOf()))
            .setSendEventsState(SessionProcessState(seqNum, mutableListOf(sessionEvent)))
            .setStatus(SessionStateType.CREATED)
            .build()

        logger.debug { "Creating new session with id $newSessionId on key $key for SessionInit sent. sessionState $newSessionState" }

        return newSessionState
    }
}
