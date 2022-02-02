package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.v5.base.util.contextLogger
import java.time.Instant

/**
 * Process a [SessionData] event to be sent to a counterparty.
 * If the current state is not CONFIRMED or CREATED it indicates a session mismatch bug, return an error message to the counterparty.
 * Set the sequence number of the outbound message and add it to the list of unacked outbound messages to be sent to a counterparty.
 */
class SessionDataProcessorSend(
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

        if (sessionState == null) {
            val errorMessage = "Tried to send SessionData for sessionState which was null. Key: $key, SessionEvent: $sessionEvent"
            logger.error(errorMessage)
            return generateErrorSessionStateFromSessionEvent(sessionId, errorMessage, "SessionData-NullSessionState", instant)
        }

        return when(val currentStatus = sessionState.status) {
            SessionStateType.ERROR -> {
                val errorMessage = "Tried to send SessionData on key $key for sessionId with status of ${SessionStateType.ERROR}. "
                logger.error(errorMessage)
                sessionState
            }
            SessionStateType.CREATED, SessionStateType.CONFIRMED ->{
                val nextSeqNum = sessionState.sendEventsState.lastProcessedSequenceNum + 1
                sessionEvent.apply {
                    sequenceNum = nextSeqNum
                    timestamp = instant.toEpochMilli()
                }

                sessionState.apply {
                    sendEventsState.lastProcessedSequenceNum = nextSeqNum
                    sendEventsState.undeliveredMessages = sendEventsState.undeliveredMessages.plus(sessionEvent)
                }
            } else -> {
                //If the session is in states CLOSING, WAIT_FOR_FINAL_ACK or CLOSED then this indicates a session mismatch as no more data
                // messages are expected to be sent. Send an error to the counterparty to inform it of the mismatch.
                val errorMessage = "Tried to send SessionData on key $key for sessionId $sessionId with status of : $currentStatus"
                logger.error(errorMessage)
                generateErrorSessionStateFromSessionEvent(sessionId, errorMessage, "SessionData-InvalidStatus", instant)
            }
        }
    }
}
