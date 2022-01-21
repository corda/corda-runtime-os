package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.SessionEventResult
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateAckEvent
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.time.Instant


/**
 * Handle receipt of a [SessionClose] event.
 * If the state is null, ERROR or CLOSED send an error response to the counterparty.
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

    override fun execute(): SessionEventResult {
        val sessionId = sessionEvent.sessionId

        return if (sessionState == null) {
            val errorMessage = "Received SessionClose on key $key and sessionId $sessionId  with null state"
            logger.error(errorMessage)
            SessionEventResult(sessionState, listOf(generateErrorEvent(sessionId, errorMessage, "SessionClose-NullSessionState", instant)))
        } else {
            val seqNum = sessionEvent.sequenceNum
            val receivedEventsState = sessionState.receivedEventsState
            val lastProcessedSequenceNum = receivedEventsState.lastProcessedSequenceNum
            when {
                seqNum == lastProcessedSequenceNum -> {
                    //duplicate
                    logger.debug {
                        "Received SessionClose on key $key and sessionId $sessionId with seqNum of $seqNum " +
                                "when last processed seqNum was $lastProcessedSequenceNum. Current SessionState: $sessionState"
                    }
                    SessionEventResult(sessionState, null)
                }
                seqNum < lastProcessedSequenceNum -> {
                    //Bug/Error
                    val errorMessage = "Received SessionClose on key $key and sessionId $sessionId with seqNum of $seqNum " +
                            "when last processed seqNum was $lastProcessedSequenceNum. Current SessionState: $sessionState"
                    logAndGenerateErrorResult(errorMessage, sessionState, sessionId, "SessionClose-InvalidSeqNum")
                }
                else -> {
                    processCloseReceived(sessionState, seqNum, sessionId, lastProcessedSequenceNum)
                }
            }
        }
    }

    private fun processCloseReceived(
        sessionState: SessionState,
        seqNum: Int,
        sessionId: String,
        lastProcessedSequenceNum: Int
    ) = when (sessionState.status) {
        SessionStateType.CONFIRMED, SessionStateType.CREATED -> {
            sessionState.status = SessionStateType.CLOSING
            SessionEventResult(sessionState, listOf(generateAckEvent(seqNum, sessionId, instant)))
        }
        SessionStateType.CLOSING -> {
            if (sessionState.sentEventsState.undeliveredMessages.isNullOrEmpty()) {
                logger.debug { "Updating session state to ${SessionStateType.CLOSED} for session state $sessionState" }
                sessionState.status = SessionStateType.CLOSED
            } else {
                logger.debug { "Updating session state to ${SessionStateType.WAIT_FOR_FINAL_ACK} for session state $sessionState" }
                sessionState.status = SessionStateType.WAIT_FOR_FINAL_ACK
            }
            SessionEventResult(sessionState, listOf(generateAckEvent(seqNum, sessionId, instant)))
        }
        SessionStateType.CLOSED -> {
            val errorMessage = "Received a SessionClose  on key $key and sessionId $sessionId with seqNum $seqNum, " +
                    "lastProcessedSequenceNum is $lastProcessedSequenceNum " +
                    "but session is already in status ${SessionStateType.CLOSED}"
            logAndGenerateErrorResult(errorMessage, sessionState, sessionId, "SessionClose-AlreadyClosed")
        }
        SessionStateType.ERROR -> {
            val errorMessage = "Received SessionClose on key $key and sessionId $sessionId when session status was " +
                    "${SessionStateType.ERROR}. SessionState: $sessionState"
            logAndGenerateErrorResult(errorMessage, sessionState, sessionId, "SessionClose-ErrorStatus")
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
    ): SessionEventResult {
        logger.error(errorMessage)
        sessionState.status = SessionStateType.ERROR
        return SessionEventResult(sessionState, listOf(generateErrorEvent(sessionId, errorMessage, errorType, instant)))
    }
}
