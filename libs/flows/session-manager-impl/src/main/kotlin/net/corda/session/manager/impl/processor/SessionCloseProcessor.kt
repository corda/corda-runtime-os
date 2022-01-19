package net.corda.session.manager.impl.processor

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.SessionEventResult
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateAckRecord
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.session.manager.impl.processor.helper.generateOutBoundRecord
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
class SessionCloseProcessor(
    private val flowKey: FlowKey,
    private val sessionState: SessionState?,
    private val sessionEvent: SessionEvent,
    private val instant: Instant
) : SessionEventProcessor {

    private companion object {
        private val logger = contextLogger()
    }

    override fun execute(): SessionEventResult {
        val sessionId = sessionEvent.sessionId

        return if (sessionEvent.messageDirection == MessageDirection.INBOUND) {
            getCloseReceivedResult(sessionId)
        } else {
            getSendCloseResult(sessionId)
        }
    }

    private fun getSendCloseResult(sessionId: String): SessionEventResult {
        return if (sessionState == null) {
            logger.error("Tried to send SessionClose with flow key $flowKey and sessionId $sessionId  with null state")
            SessionEventResult(null, null)
        } else {
            //TODO - add error handling. if there is still messages in sessionState.receivedEventsState.undeliveredMessages then we
            // shouldn't be able to send close. indicates bug

            val sentEventState = sessionState.sentEventsState
            val nextSeqNum = sentEventState.lastProcessedSequenceNum + 1
            val undeliveredMessages = sentEventState.undeliveredMessages?.toMutableList() ?: mutableListOf()
            undeliveredMessages.add(sessionEvent)
            sessionEvent.sequenceNum = nextSeqNum
            sessionEvent.timestamp = instant.toEpochMilli()
            sentEventState.lastProcessedSequenceNum = nextSeqNum

            when (val currentState = sessionState.status) {
                SessionStateType.CONFIRMED -> {
                    sessionState.status = SessionStateType.CLOSING
                    SessionEventResult(sessionState, generateOutBoundRecord(sessionEvent, sessionId))
                }
                SessionStateType.CLOSING -> {
                    //Doesn't go to closed until ack received
                    sessionState.status = SessionStateType.WAIT_FOR_FINAL_ACK
                    SessionEventResult(sessionState, generateOutBoundRecord(sessionEvent, sessionId))
                }
                SessionStateType.CLOSED -> {
                    //session is already completed successfully. Indicates bug. should we send an error back and change state to error
                    logger.error("Tried to send SessionClose on key $flowKey and sessionId $sessionId, session status is " +
                            "$currentState. Current SessionState: $sessionState")
                    SessionEventResult(sessionState, null)
                }
                else -> {
                    val errorMessage = "Tried to send SessionClose on key $flowKey and sessionId $sessionId, session status is " +
                            "$currentState. Current SessionState: $sessionState. Sending error to counterparty"
                    logAndGenerateErrorResult(errorMessage, sessionState, sessionId, "SessionClose-InvalidStatus")
                }
            }
        }
    }

    private fun getCloseReceivedResult(
        sessionId: String
    ): SessionEventResult {
        return if (sessionState == null) {
            val errorMessage = "Received SessionClose on key $flowKey and sessionId $sessionId  with null state"
            logger.error(errorMessage)
            SessionEventResult(sessionState, generateErrorEvent(sessionId, errorMessage, "SessionClose-NullSessionState", instant))
        } else {
            val seqNum = sessionEvent.sequenceNum
            val receivedEventsState = sessionState.receivedEventsState
            val lastProcessedSequenceNum = receivedEventsState.lastProcessedSequenceNum
            when {
                seqNum == lastProcessedSequenceNum -> {
                    //duplicate
                    logger.debug {
                        "Received SessionClose on key $flowKey and sessionId $sessionId with seqNum of $seqNum " +
                                "when last processed seqNum was $lastProcessedSequenceNum. Current SessionState: $sessionState"
                    }
                    SessionEventResult(sessionState, null)
                }
                seqNum < lastProcessedSequenceNum -> {
                    //Bug/Error
                    val errorMessage = "Received SessionClose on key $flowKey and sessionId $sessionId with seqNum of $seqNum " +
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
            SessionEventResult(sessionState, generateAckRecord(seqNum, sessionId, instant))
        }
        SessionStateType.CLOSING -> {
            if (sessionState.sentEventsState.undeliveredMessages.isNullOrEmpty()) {
                logger.debug { "Updating session state to ${SessionStateType.CLOSED} for session state $sessionState" }
                sessionState.status = SessionStateType.CLOSED
            } else {
                logger.debug { "Updating session state to ${SessionStateType.WAIT_FOR_FINAL_ACK} for session state $sessionState" }
                sessionState.status = SessionStateType.WAIT_FOR_FINAL_ACK
            }
            SessionEventResult(sessionState, generateAckRecord(seqNum, sessionId, instant))
        }
        SessionStateType.CLOSED -> {
            val errorMessage = "Received a SessionClose  on key $flowKey and sessionId $sessionId with seqNum $seqNum, " +
                    "lastProcessedSequenceNum is $lastProcessedSequenceNum " +
                    "but session is already in status ${SessionStateType.CLOSED}"
            logAndGenerateErrorResult(errorMessage, sessionState, sessionId, "SessionClose-AlreadyClosed")
        }
        SessionStateType.ERROR -> {
            val errorMessage = "Received SessionClose on key $flowKey and sessionId $sessionId when session status was " +
                    "${SessionStateType.ERROR}. SessionState: $sessionState"
            logAndGenerateErrorResult(errorMessage, sessionState, sessionId, "SessionClose-ErrorStatus")
        }
        else -> {
            val errorMessage = "Received SessionClose on key $flowKey and sessionId $sessionId when session status was " +
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
        return SessionEventResult(sessionState, generateErrorEvent(sessionId, errorMessage, errorType, instant))
    }
}
