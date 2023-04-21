package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.state.session.SessionState
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
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
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun execute(): SessionState {
        return if (sessionState == null) {
            val errorMessage = "Received SessionAck on key $key for sessionId ${sessionEvent.sessionId} which had null state"
            logger.debug { errorMessage }
            generateErrorSessionStateFromSessionEvent(errorMessage, sessionEvent, "SessionAck-NullState", instant)
        } else {
            logger.debug {
                "Received SessionAck on key $key with receivedSequenceNum ${sessionEvent.receivedSequenceNum} and outOfOrderSequenceNums " +
                        "${sessionEvent.outOfOrderSequenceNums} for session state: $sessionState"
            }
            return sessionState
        }
    }
}
