package net.corda.session.manager.impl.processor

import net.corda.data.flow.FlowKey
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.SessionEventResult
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.time.Instant

/**
 * Generate a [SessionAck] for the received event.
 * If the current session state has a status of WAIT_FOR_FINAL_ACK then this is the final ACK of the session close message
 * and so the session can be set to CLOSED.
 */
class SessionAckProcessor(
    private val flowKey: FlowKey,
    private val sessionState: SessionState?,
    private val sessionId: String,
    private val sequenceNum: Int,
    private val instant: Instant
) : SessionEventProcessor {

    private companion object {
        private val logger = contextLogger()
    }

    override fun execute(): SessionEventResult {
        return if (sessionState == null) {
            val errorMessage = "Received SessionAck on key $flowKey for sessionId $sessionId which had null state"
            logger.error(errorMessage)
            SessionEventResult(sessionState, generateErrorEvent(sessionId, errorMessage, "SessionAck-NullState", instant))
        } else {
            logger.debug { "Received SessionAck on key $flowKey for seqNum $sequenceNum for session state: $sessionState" }
            val undeliveredMessages = sessionState.sentEventsState.undeliveredMessages
            undeliveredMessages.removeIf { it.sequenceNum == sequenceNum}

            if (sessionState.status == SessionStateType.WAIT_FOR_FINAL_ACK && undeliveredMessages.isEmpty()) {
                logger.debug { "Updating session state to ${SessionStateType.CLOSED} for session state $sessionState" }
                sessionState.status = SessionStateType.CLOSED
            } else if (sessionState.status == SessionStateType.CREATED && undeliveredMessages.isEmpty()) {
                logger.debug { "Updating session state to ${SessionStateType.CONFIRMED} for session state $sessionState" }
                sessionState.status = SessionStateType.CONFIRMED
            }

            SessionEventResult(sessionState, null)
        }
    }
}
