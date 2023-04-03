package net.corda.session.manager.impl.processor

import java.time.Instant
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.utils.KeyValueStore
import net.corda.session.manager.Constants
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.utilities.trace
import org.slf4j.LoggerFactory

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
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val isInteropSessionInit = run {
        val sessionInit = sessionEvent.payload as SessionInit
        sessionInit.contextSessionProperties?.let {
            KeyValueStore(it)[Constants.FLOW_PROTOCOL_INTEROP]?.equals("true")
        } ?: false
    }

    override fun execute(): SessionState {
        if (sessionState != null) {
            logger.warn("Tried to send SessionInit on key $key for session which was not null: $sessionState")
            return sessionState.apply {
                status = SessionStateType.ERROR
                sendEventsState.undeliveredMessages = sendEventsState.undeliveredMessages.plus(
                    generateErrorEvent(sessionState,
                        sessionEvent,
                        "Tried to send SessionInit for session which is not null",
                        "SessionInit-SessionMismatch", instant
                    )
                )
            }
        }

        val newSessionId = sessionEvent.sessionId
        val seqNum = 1

        sessionEvent.apply {
            sequenceNum = seqNum
            timestamp = instant
        }

        val newSessionState = SessionState.newBuilder()
            .setSessionId(newSessionId)
            .setIsInteropSession(isInteropSessionInit)
            .setSessionStartTime(instant)
            .setLastReceivedMessageTime(instant)
            .setLastSentMessageTime(instant)
            .setSendAck(false)
            .setCounterpartyIdentity(sessionEvent.initiatedIdentity)
            .setReceivedEventsState(SessionProcessState(0, mutableListOf()))
            .setSendEventsState(SessionProcessState(seqNum, mutableListOf(sessionEvent)))
            .setCounterpartySessionProperties(null)
            .setStatus(SessionStateType.CREATED)
            .setHasScheduledCleanup(false)
            .build()

        logger.trace { "Creating new session with id $newSessionId on key $key for SessionInit sent. sessionState $newSessionState" }

        return newSessionState
    }
}
