package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.SessionEventResult
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.v5.base.util.contextLogger
import java.time.Instant

/**
 * Process a [SessionData] event to be sent to a counterparty.
 * If the current state is not confirmed it indicates a session mismatch bug, return an error message to the counterparty.
 * Set the sequence number of the outbound message and add it to the list of unacked outbound messages
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

    override fun execute(): SessionEventResult {
        val sessionId = sessionEvent.sessionId

        if (sessionState == null) {
            val errorMessage = "Tried to send SessionData for sessionState which was null. Key: $key, SessionEvent: $sessionEvent"
            logger.error(errorMessage)
            return SessionEventResult(
                sessionState,
                listOf(generateErrorEvent(sessionId, errorMessage, "SessionData-NullSessionState", instant)))
        }

        return getOutboundDataEventResult(sessionState, sessionId)
    }

    private fun getOutboundDataEventResult(
        sessionState: SessionState,
        sessionId: String
    ): SessionEventResult {
        val currentStatus = sessionState.status

        if (currentStatus == SessionStateType.ERROR) {
            val errorMessage = "Tried to send SessionData on key $key for sessionId with status of ${SessionStateType.ERROR}. "
            logger.error(errorMessage)
            return SessionEventResult(sessionState, null)
        } else if (currentStatus != SessionStateType.CONFIRMED || currentStatus != SessionStateType.CREATED) {
            //If the session is in states CLOSING, WAIT_FOR_FINAL_ACK or CLOSED then this indicates a session mismatch as no more data
            // messages are expected to be sent. Send an error to the counterparty to inform it of the mismatch.
            val errorMessage = "Received SessionData on key $key for sessionId with status of : $currentStatus"
            logger.error(errorMessage)
            return SessionEventResult(
                sessionState, listOf(
                    generateErrorEvent(
                        sessionId, errorMessage, "SessionData-InvalidStatus",
                        instant
                    )
                )
            )
        }

        val sentEventState = sessionState.sentEventsState
        val nextSeqNum = sentEventState.lastProcessedSequenceNum + 1
        val undeliveredMessages = sentEventState.undeliveredMessages?.toMutableList() ?: mutableListOf()
        sentEventState.lastProcessedSequenceNum = nextSeqNum
        sessionEvent.sequenceNum = nextSeqNum
        sessionEvent.timestamp = instant.toEpochMilli()
        undeliveredMessages.add(sessionEvent)
        sentEventState.undeliveredMessages = undeliveredMessages
        return SessionEventResult(sessionState, listOf(sessionEvent))
    }
}
