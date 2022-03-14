package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.state.session.SessionState
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.time.Instant

/**
 * Process a [SessionAck] received.
 * If state is null return a new error state with queued to the counterparty.
 * Log the event received. Logic to remove events from the send events state and update status is applied in [SessionManagerImpl]
 */
class SessionAckProcessorReceive(
    private val key: Any,
    private val sessionState: SessionState?,
    private val sessionEvent: SessionEvent,
    private val instant: Instant
) : SessionEventProcessor {

    private companion object {
        private val logger = contextLogger()
    }

    override fun execute(): SessionState {
        val sessionId = sessionEvent.sessionId

        return if (sessionState == null) {
            val errorMessage = "Received SessionAck on key $key for sessionId $sessionId which had null state"
            logger.error(errorMessage)
            generateErrorSessionStateFromSessionEvent(sessionId, errorMessage, "SessionAck-NullState", instant)
        } else {
            logger.debug {
                "Received SessionAck on key $key with receivedSequenceNum ${sessionEvent.receivedSequenceNum} and outOfOrderSequenceNums " +
                        "${sessionEvent.outOfOrderSequenceNums} for session state: $sessionState"
            }
            return sessionState
        }
    }
}
