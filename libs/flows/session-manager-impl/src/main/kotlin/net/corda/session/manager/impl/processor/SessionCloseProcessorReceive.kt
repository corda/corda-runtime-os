package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateAckEvent
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.time.Instant


/**
 * Handle receipt of a [SessionClose] event.
 * If the message is a duplicate send an Ack back as it may be from a resend.
 * If the state is null, or status is ERROR, WAIT_FOR_FINAL_ACK or CLOSED send an error response to the counterparty.
 * If the state is CONFIRMED/CREATED and the sequence number is valid then set the status to CLOSING and return a [SessionAck]
 * If the state is CLOSING and it is not a duplicate then check the state and see if all sent messages have been acknowledged by the
 * counterparty. If they have then set the status to be CLOSED. If they haven't then set the state to WAIT_FOR_FINAL_ACK
 */
class SessionCloseProcessorReceive(
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
            val errorMessage = "Received SessionClose on key $key and sessionId $sessionId  with null state"
            logger.error(errorMessage)
            generateErrorSessionStateFromSessionEvent(sessionId, errorMessage, "SessionClose-NullSessionState", instant)
        } else {
            val seqNum = sessionEvent.sequenceNum
            val receivedEventsState = sessionState.receivedEventsState
            val lastProcessedSequenceNum = receivedEventsState.lastProcessedSequenceNum
            if (seqNum <= lastProcessedSequenceNum) {
                //duplicate
                logger.debug {
                    "Received SessionClose on key $key and sessionId $sessionId with seqNum of $seqNum " +
                            "when last processed seqNum was $lastProcessedSequenceNum. Current SessionState: $sessionState"
                }
                sessionState.apply {
                    sentEventsState.undeliveredMessages =
                        sessionState.sentEventsState.undeliveredMessages.plus(generateAckEvent(seqNum, sessionId, instant))
                }
            } else {
                processCloseReceivedAndGetState(sessionState, seqNum, sessionId)
            }
        }
    }

    private fun processCloseReceivedAndGetState(
        sessionState: SessionState,
        seqNum: Int,
        sessionId: String
    ) = when (sessionState.status) {
        SessionStateType.CONFIRMED, SessionStateType.CREATED -> {
            sessionState.status = SessionStateType.CLOSING
            sessionState.sentEventsState.undeliveredMessages =
                sessionState.sentEventsState.undeliveredMessages.plus(generateAckEvent(seqNum, sessionId, instant))
            sessionState
        }
        SessionStateType.CLOSING -> {
            if (sessionState.sentEventsState.undeliveredMessages.isNullOrEmpty()) {
                logger.debug { "Updating session state to ${SessionStateType.CLOSED} for session state $sessionState" }
                sessionState.status = SessionStateType.CLOSED
            } else {
                logger.debug { "Updating session state to ${SessionStateType.WAIT_FOR_FINAL_ACK} for session state $sessionState" }
                sessionState.status = SessionStateType.WAIT_FOR_FINAL_ACK
            }
            sessionState.sentEventsState.undeliveredMessages =
                sessionState.sentEventsState.undeliveredMessages.plus(generateAckEvent(seqNum, sessionId, instant))
            sessionState
        }
        else -> {
            val errorMessage = "Received SessionClose on key $key and sessionId $sessionId when session status was " +
                    "${sessionState.status}. SessionState: $sessionState"
            logAndGenerateErrorResult(errorMessage, sessionState, sessionId, "SessionClose-InvalidStatus")
        }
    }

    private fun logAndGenerateErrorResult(
        errorMessage: String,
        sessionState: SessionState,
        sessionId: String,
        errorType: String
    ): SessionState {
        logger.error(errorMessage)
        sessionState.status = SessionStateType.ERROR
        sessionState.sentEventsState.undeliveredMessages =
            sessionState.sentEventsState.undeliveredMessages.plus(generateErrorEvent(sessionId, errorMessage, errorType, instant))
        return sessionState
    }
}
