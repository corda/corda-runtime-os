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
 * Handle send of a [SessionClose] event.
 * If the state is null, ERROR or CLOSED send an error response to the counterparty as this indicates a bug in client code and a mismatch.
 * If the state is CONFIRMED and the sequence number is valid then set the status to CLOSING
 * If the state is CLOSING and it is not a duplicate then set the status to WAIT_FOR_FINAL_ACK. The session cannot be closed until all
 * acks are received by the counterparty.
 */
class SessionCloseProcessorSend(
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
        return when {
            sessionState == null -> {
                logger.error("Tried to send SessionClose with flow key $key and sessionId $sessionId  with null state")
                SessionEventResult(null, null)
            }
            //session mismatch error
            sessionState.receivedEventsState.undeliveredMessages.isNotEmpty() -> {
                val errorMessage = "Tried to send SessionClose on key $key and sessionId $sessionId, session status is " +
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

                getResultByCurrentState(sessionState, sessionId)
            }
        }
    }

    private fun getResultByCurrentState(
        sessionState: SessionState,
        sessionId: String
    ) = when (val currentState = sessionState.status) {
        //TODO - is this valid?
        // session may have no data messages to send/receive and so flow can execute to the end
        // session wont be closed until acks are received regardless but theres no reason to wait?
        SessionStateType.CREATED -> {
            sessionState.status = SessionStateType.CLOSING
            SessionEventResult(sessionState, listOf(sessionEvent))
        }
        SessionStateType.CONFIRMED -> {
            sessionState.status = SessionStateType.CLOSING
            SessionEventResult(sessionState, listOf(sessionEvent))
        }
        SessionStateType.CLOSING -> {
            //Doesn't go to closed until ack received
            sessionState.status = SessionStateType.WAIT_FOR_FINAL_ACK
            SessionEventResult(sessionState, listOf(sessionEvent))
        }
        SessionStateType.CLOSED -> {
            //session is already completed successfully. Indicates bug. should we send an error back and change state to error
            logger.error(
                "Tried to send SessionClose on key $key and sessionId $sessionId, session status is " +
                        "$currentState. Current SessionState: $sessionState"
            )
            SessionEventResult(sessionState, null)
        }
        else -> {
            val errorMessage = "Tried to send SessionClose on key $key and sessionId $sessionId, session status is " +
                    "$currentState. Current SessionState: $sessionState. Sending error to counterparty"
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
