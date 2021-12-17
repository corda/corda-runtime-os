package net.corda.session.manager.impl.processor

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.SessionEventResult
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateAckRecord
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.time.Clock


/**
 * Handle receipt of a [SessionClose] event.
 * If the state is null, ERROR or CLOSED send an error response to the counterparty.
 * If the state is CONFIRMED/CREATED and the sequence number is valid then set the status to CLOSING and return a [SessionAck]
 * If the state is CLOSING and it is not a duplicate then check the state and see if all sent messages have been acknowledged by the
 * counterparty. If they have then set the status to be CLOSED. If they haven't then set the state to WAIT_FOR_FINAL_ACK
 */
class SessionCloseProcessor(
    private val flowKey: FlowKey,
    private val sessionState: SessionState?,
    private val sessionEvent: SessionEvent,
    private val clock: Clock = Clock.systemUTC()
) : SessionEventProcessor {

    private companion object {
        private val logger = contextLogger()
    }

    override fun execute(): SessionEventResult {
        val sessionId = sessionEvent.sessionId

        return if (sessionState == null) {
            val errorMessage = "Received SessionClose on key $flowKey and sessionId $sessionId  with null state"
            logger.error(errorMessage)
            SessionEventResult(sessionState, generateErrorEvent(sessionId, errorMessage, "SessionClose-NullSessionState", clock))
        } else {
            val seqNum = sessionEvent.sequenceNum
            val receivedEventsState = sessionState.receivedEventsState
            val lastProcessedSequenceNum = receivedEventsState.lastProcessedSequenceNum
            when {
                seqNum == lastProcessedSequenceNum -> {
                    //duplicate
                    logger.debug { "Received SessionClose on key $flowKey and sessionId $sessionId with seqNum of $seqNum " +
                            "when last processed seqNum was $lastProcessedSequenceNum. Current SessionState: $sessionState" }
                    SessionEventResult(sessionState, null)
                }
                seqNum < lastProcessedSequenceNum -> {
                    //Bug/Error
                    val errorMessage = "Received SessionClose on key $flowKey and sessionId $sessionId with seqNum of $seqNum " +
                            "when last processed seqNum was $lastProcessedSequenceNum. Current SessionState: $sessionState"
                    logErrorAndReturnResult(errorMessage, sessionState, sessionId, "SessionClose-InvalidSeqNum")
                }
                else -> {
                    processClose(sessionState, seqNum, sessionId, lastProcessedSequenceNum)
                }
            }
        }
    }

    private fun processClose(
        sessionState: SessionState,
        seqNum: Int,
        sessionId: String,
        lastProcessedSequenceNum: Int
    ) = when (sessionState.status) {
        SessionStateType.CONFIRMED, SessionStateType.CREATED -> {
            sessionState.status = SessionStateType.CLOSING
            SessionEventResult(sessionState, generateAckRecord(seqNum, sessionId, clock))
        }
        SessionStateType.CLOSING -> {
            if (sessionState.sentEventsState.undeliveredMessages.isNullOrEmpty()) {
                logger.debug { "Updating session state to ${SessionStateType.CLOSED} for session state $sessionState" }
                sessionState.status = SessionStateType.CLOSED
            } else {
                logger.debug { "Updating session state to ${SessionStateType.WAIT_FOR_FINAL_ACK} for session state $sessionState" }
                sessionState.status = SessionStateType.WAIT_FOR_FINAL_ACK
            }
            SessionEventResult(sessionState, generateAckRecord(seqNum, sessionId, clock))
        }
        SessionStateType.CLOSED -> {
            val errorMessage = "Received a SessionClose  on key $flowKey and sessionId $sessionId with seqNum $seqNum, " +
                    "lastProcessedSequenceNum is $lastProcessedSequenceNum " +
                    "but session is already in status ${SessionStateType.CLOSED}"
            logErrorAndReturnResult(errorMessage, sessionState, sessionId, "SessionClose-AlreadyClosed")
        }
        SessionStateType.ERROR -> {
            val errorMessage = "Received SessionClose on key $flowKey and sessionId $sessionId when session status was " +
                    "${SessionStateType.ERROR}. SessionState: $sessionState"
            logErrorAndReturnResult(errorMessage, sessionState, sessionId, "SessionClose-ErrorStatus")
        }
        else -> {
            val errorMessage = "Received SessionClose on key $flowKey and sessionId $sessionId when session status was " +
                    "${sessionState.status}. SessionState: $sessionState"
            logErrorAndReturnResult(errorMessage, sessionState, sessionId, "SessionClose-InvalidStatus")
        }
    }

    private fun logErrorAndReturnResult(
        errorMessage: String,
        sessionState: SessionState,
        sessionId: String,
        errorType: String
    ): SessionEventResult {
        logger.error(errorMessage)
        sessionState.status = SessionStateType.ERROR
        return SessionEventResult(sessionState, generateErrorEvent(sessionId, errorMessage, errorType, clock))
    }
}
