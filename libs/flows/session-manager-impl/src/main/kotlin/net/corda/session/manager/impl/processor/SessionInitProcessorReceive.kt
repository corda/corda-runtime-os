package net.corda.session.manager.impl.processor

import java.time.Instant
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.slf4j.LoggerFactory

/**
 * Process SessionInit messages.
 * Generate [SessionAck] for the SessionInit and create a new [SessionState].
 * If [SessionState] for the given sessionId is not null log the duplicate event.
 * If SessionInit is received in reply to a SessionInit sent, error the session.
 */
class SessionInitProcessorReceive(
    private val key: Any,
    private val sessionState: SessionState?,
    private val sessionEvent: SessionEvent,
    private val instant: Instant
) : SessionEventProcessor {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun execute(): SessionState {
        return if (sessionState != null) {
            val seqNum = sessionEvent.sequenceNum
            if (sessionState.status == SessionStateType.CREATED || seqNum > 1) {
                sessionState.apply {
                    status = SessionStateType.ERROR
                    sendEventsState.undeliveredMessages = sendEventsState.undeliveredMessages.plus(
                            generateErrorEvent(sessionState,
                                sessionEvent,
                                "Received SessionInit with seqNum $seqNum when session state which was not null: $sessionState",
                                "SessionInit-SessionMismatch",
                                instant
                            )
                        )
                }
            } else {
                logger.debug { "Received duplicate SessionInit on key $key for session which was not null: $sessionState" }
                sessionState
            }
        } else {
            val sessionId = sessionEvent.sessionId
            val sessionInit: SessionInit = sessionEvent.payload as SessionInit
            val seqNum = sessionEvent.sequenceNum
            val newSessionState = SessionState.newBuilder()
                .setSessionId(sessionId)
                .setSessionStartTime(instant)
                .setLastReceivedMessageTime(instant)
                .setCounterpartyIdentity(sessionEvent.initiatingIdentity)
                .setReceivedEventsState(SessionProcessState(seqNum, mutableListOf(sessionEvent)))
                .setSendEventsState(SessionProcessState(0, mutableListOf()))
                .setStatus(SessionStateType.CONFIRMED)
                .setHasScheduledCleanup(false)
                .setCounterpartySessionProperties(sessionInit.contextSessionProperties)
                .build()

            logger.trace { "Created new session with id $sessionId for SessionInit received on key $key. sessionState $newSessionState" }
            return newSessionState
        }
    }
}
