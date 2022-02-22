package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.v5.base.util.contextLogger
import java.time.Instant


/**
 * Handle send of a [SessionClose] event.
 * If the state is null, ERROR or CLOSED send an error response to the counterparty as this indicates a bug in client code or  a
 * session mismatch has occurred.
 * If the client has not consumed all received events and it tries to send a close then trigger an error as this is a bug/session mismatch.
 * If the client has already sent an close trigger an error as this is a bug/session mismatch.
 * If the state is CONFIRMED then set the status to CLOSING
 * If the state is CLOSING and set the status to WAIT_FOR_FINAL_ACK. The session cannot be closed until all acks are received by the
 * counterparty.
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

    override fun execute(): SessionState {
        val sessionId = sessionEvent.sessionId
        val currentStatus = sessionState?.status
        return when {
            sessionState == null -> {
                handleNullSession(sessionId)
            }
            currentStatus == SessionStateType.ERROR || currentStatus == SessionStateType.WAIT_FOR_FINAL_ACK || currentStatus ==
                    SessionStateType.CLOSED -> {
                handleInvalidStatus(sessionState)
            }
            sessionState.receivedEventsState.undeliveredMessages.any { it.payload !is SessionClose } -> {
                //session mismatch error, ignore close messages as we only care about data messages not consumed by the client lib
                handleUnprocessedReceivedDataEvents(sessionId, sessionState)
            }
            currentStatus == SessionStateType.CLOSING &&
                    sessionState.receivedEventsState.undeliveredMessages.none { it.payload is SessionClose } -> {
                //session mismatch - tried to send multiple close
                handleDuplicateCloseSent(sessionState)
            }
            else -> {
                val nextSeqNum = sessionState.sendEventsState.lastProcessedSequenceNum + 1
                sessionEvent.sequenceNum = nextSeqNum
                getResultByCurrentState(sessionState, sessionId, nextSeqNum)
            }
        }
    }

    private fun handleNullSession(sessionId: String): SessionState {
        val errorMessage = "Tried to send SessionClose with flow key $key and sessionId $sessionId  with null state"
        logger.error(errorMessage)
        return generateErrorSessionStateFromSessionEvent(sessionId, errorMessage, "SessionCLose-StateNull", instant)
    }

    private fun handleDuplicateCloseSent(
        sessionState: SessionState
    ): SessionState {
        val sessionId = sessionState.sessionId
        val errorMessage = "Tried to send SessionClose on key $key and sessionId $sessionId, session status is " +
                "${sessionState.status}, however SessionClose has already been sent. " +
                "Current SessionState: $sessionState."
        return logAndGenerateErrorResult(errorMessage, sessionState, sessionId, "SessionClose-SessionMismatch")
    }

    private fun handleUnprocessedReceivedDataEvents(
        sessionId: String,
        sessionState: SessionState
    ): SessionState {
        val errorMessage = "Tried to send SessionClose on key $key and sessionId $sessionId, session status is " +
                "${sessionState.status}, however there are still received events that have not been processed. " +
                "Current SessionState: $sessionState."
        return logAndGenerateErrorResult(errorMessage, sessionState, sessionId, "SessionClose-SessionMismatch")
    }

    private fun handleInvalidStatus(
        sessionState: SessionState
    ) : SessionState {
        val errorMessage = "Tried to send SessionClose on key $key for sessionId ${sessionState.sessionId} " +
                "with status of : ${sessionState.status}"
        logger.error(errorMessage)
        return sessionState.apply {
            status = SessionStateType.ERROR
            sendEventsState.undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.plus(
                generateErrorEvent(sessionId, errorMessage, "SessionClose-InvalidStatus", instant)
            )
        }
    }

    private fun getResultByCurrentState(
        sessionState: SessionState,
        sessionId: String,
        nextSeqNum: Int,
    ) = when (val currentState = sessionState.status) {
        SessionStateType.CONFIRMED -> {
            sessionState.apply {
                status = SessionStateType.CLOSING
                sendEventsState.lastProcessedSequenceNum = nextSeqNum
                sendEventsState.undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.plus(sessionEvent)
            }
        }
        SessionStateType.CLOSING -> {
            //Doesn't go to closed until ack received
            sessionState.apply {
                status = SessionStateType.WAIT_FOR_FINAL_ACK
                sendEventsState.lastProcessedSequenceNum = nextSeqNum
                sendEventsState.undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.plus(sessionEvent)
            }
        }
        else -> {
            val errorMessage = "Tried to send SessionClose on key $key and sessionId $sessionId, session status is " +
                    "$currentState. Current SessionState: $sessionState."
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
        return generateErrorSessionStateFromSessionEvent(sessionId, errorMessage, errorType, instant)
    }
}
