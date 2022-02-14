package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.time.Instant

/**
 * Process a [SessionAck] received.
 * Remove the message from undelivered sent events for the sequence number received in the ack.
 * If the current session state has a status of WAIT_FOR_FINAL_ACK then this is the final ACK of the session close message
 * and so the session can be set to CLOSED.
 * If the current session state has a status of CREATED and the SessionInit has been acked then the session can be set to CONFIRMED
 *
 */
class SessionAckProcessorReceived(
    private val key: Any,
    private val sessionState: SessionState?,
    private val sessionId: String,
    private val sequenceNum: Int,
    private val instant: Instant
) : SessionEventProcessor {

    private companion object {
        private val logger = contextLogger()
    }

    override fun execute(): SessionState {
        return if (sessionState == null) {
            val errorMessage = "Received SessionAck on key $key for sessionId $sessionId which had null state"
            logger.error(errorMessage)
            generateErrorSessionStateFromSessionEvent(sessionId, errorMessage, "SessionAck-NullState", instant)
        } else {
            logger.debug { "Received SessionAck on key $key for seqNum $sequenceNum for session state: $sessionState" }

            sessionState.apply {
                sendEventsState.undeliveredMessages.removeIf { it.sequenceNum == sequenceNum}
                val nonAckUndeliveredMessages = sendEventsState.undeliveredMessages.filter { it.payload !is SessionAck }
                if (sessionState.status == SessionStateType.WAIT_FOR_FINAL_ACK && nonAckUndeliveredMessages.isEmpty()) {
                    logger.debug { "Updating session state to ${SessionStateType.CLOSED} for session state $sessionState" }
                    sessionState.status = SessionStateType.CLOSED
                } else if (sessionState.status == SessionStateType.CREATED && nonAckUndeliveredMessages.isEmpty()) {
                    logger.debug { "Updating session state to ${SessionStateType.CONFIRMED} for session state $sessionState" }
                    sessionState.status = SessionStateType.CONFIRMED
                }
            }
        }
    }
}
