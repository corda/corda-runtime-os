package net.corda.session.manager.impl.processor

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.SessionEventResult
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.session.manager.impl.processor.helper.generateOutBoundRecord
import net.corda.v5.base.util.contextLogger
import java.time.Instant


/**
 * Handle send of a [SessionClose] event.
 * If the state is null, ERROR or CLOSED send an error response to the counterparty as this indicates a bug in client code and a mismatch.
 * If the state is CONFIRMED and the sequence number is valid then set the status to CLOSING
 * If the state is CLOSING and it is not a duplicate then set the status to WAIT_FOR_FINAL_ACK. The session cannot be closed until all
 * acks are received by the counterparty.
 */
class SessionCloseProcessorSend(
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
        return when {
            sessionState == null -> {
                logger.error("Tried to send SessionClose with flow key $flowKey and sessionId $sessionId  with null state")
                SessionEventResult(null, null)
            }
            sessionState.receivedEventsState.undeliveredMessages.isNotEmpty() -> {
                val errorMessage = "Tried to send SessionClose on key $flowKey and sessionId $sessionId, session status is " +
                        "${sessionState.status}, however there are still received events that have not been processed. " +
                        "Current SessionState: $sessionState. Sending error to counterparty"
                logAndGenerateErrorResult(errorMessage, sessionState, sessionId, "SessionClose-SessionMismatch")
            }
            else -> {
                val sentEventState = sessionState.sentEventsState
                val nextSeqNum = sentEventState.lastProcessedSequenceNum + 1
                val undeliveredMessages = sentEventState.undeliveredMessages?.toMutableList() ?: mutableListOf()
                undeliveredMessages.add(sessionEvent)
                sessionEvent.sequenceNum = nextSeqNum
                sessionEvent.timestamp = instant.toEpochMilli()
                sentEventState.lastProcessedSequenceNum = nextSeqNum

                when (val currentState = sessionState.status) {
                    //TODO - we could be in a state of CREATED if there is no data messages in the flow
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
