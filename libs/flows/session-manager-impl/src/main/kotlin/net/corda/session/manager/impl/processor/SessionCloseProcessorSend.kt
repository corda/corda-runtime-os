package net.corda.session.manager.impl.processor

import java.time.Instant
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory

/**
 * Handle send of a [SessionClose] event.
 * If the state is null, ERROR or CLOSED send an error response to the counterparty as this indicates a bug in client code or  a
 * session mismatch has occurred.
 * If the client has not consumed all received events and it tries to send a close then trigger an error as this is a bug/session mismatch.
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
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun execute(): SessionState {
        val sessionId = sessionEvent.sessionId
        val currentStatus = sessionState?.status
        return when {
            sessionState == null -> {
                handleNullSession(sessionId)
            }
            currentStatus == SessionStateType.CLOSED || currentStatus == SessionStateType.WAIT_FOR_FINAL_ACK -> {
                sessionState
            }
            currentStatus == SessionStateType.ERROR -> {
                handleInvalidStatus(sessionState)
            }
            hasUnprocessedReceivedDataEvents(sessionState) -> {
                handleUnprocessedReceivedDataEvents(sessionId, sessionState)
            }
            isClosingWithClosesToSend(currentStatus, sessionState) -> {
                logger.debug { "Already have a close to send, $sessionId" }
                sessionState
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
        logger.warn(errorMessage)
        return generateErrorSessionStateFromSessionEvent(errorMessage, sessionEvent, "SessionCLose-StateNull", instant)
    }

    private fun hasUnprocessedReceivedDataEvents(sessionState: SessionState): Boolean {
        return sessionState.receivedEventsState.undeliveredMessages.any { it.payload !is SessionClose }
    }

    private fun handleUnprocessedReceivedDataEvents(
        sessionId: String,
        sessionState: SessionState
    ): SessionState {
        val errorMessage = "Tried to send SessionClose on key $key and sessionId $sessionId, session status is " +
                "${sessionState.status}, however there are still received events that have not been processed. " +
                "Current SessionState: $sessionState."
        return logAndGenerateErrorResult(errorMessage, sessionState, "SessionClose-SessionMismatch")
    }

    private fun handleInvalidStatus(
        sessionState: SessionState
    ): SessionState {
        val errorMessage = "Tried to send SessionClose on key $key for sessionId ${sessionState.sessionId} " +
                "with status of : ${sessionState.status}"
        logger.warn(errorMessage)
        return sessionState.apply {
            status = SessionStateType.ERROR
            sendEventsState.undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.plus(
                generateErrorEvent(sessionState, sessionEvent, errorMessage, "SessionClose-InvalidStatus", instant)
            )
        }
    }

    private fun isClosingWithClosesToSend(currentStatus: SessionStateType?, sessionState: SessionState): Boolean {
        return currentStatus == SessionStateType.CLOSING &&
                sessionState.receivedEventsState.undeliveredMessages.none { it.payload is SessionClose }
    }

    private fun getResultByCurrentState(
        sessionState: SessionState,
        sessionId: String,
        nextSeqNum: Int,
    ) = when (val currentState = sessionState.status) {
        SessionStateType.CONFIRMED -> {
            sessionState.apply {
                logger.trace { "Currently in CONFIRMED. Changing to CLOSING. nextSeqNum: $nextSeqNum, adding this event to send " +
                        "${sessionEvent.sequenceNum}, $sessionId" }
                status = SessionStateType.CLOSING
                sendEventsState.lastProcessedSequenceNum = nextSeqNum
                sendEventsState.undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.plus(sessionEvent)
            }
        }
        SessionStateType.CLOSING -> {
            logger.trace { "Currently in CLOSING. Changing to WAIT_FOR_FINAL_ACK. nextSeqNum: $nextSeqNum, adding this event to send " +
                    "${sessionEvent.sequenceNum}, $sessionId" }
            // Doesn't go to closed until ack received
            sessionState.apply {
                status = SessionStateType.WAIT_FOR_FINAL_ACK
                sendEventsState.lastProcessedSequenceNum = nextSeqNum
                sendEventsState.undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.plus(sessionEvent)
            }
        }
        else -> {
            val errorMessage = "Tried to send SessionClose on key $key and sessionId $sessionId, session status is " +
                    "$currentState. Current SessionState: $sessionState."
            logAndGenerateErrorResult(errorMessage, sessionState, "SessionClose-InvalidStatus")
        }
    }

    private fun logAndGenerateErrorResult(
        errorMessage: String,
        sessionState: SessionState,
        errorType: String
    ): SessionState {
        logger.warn(errorMessage)
        sessionState.status = SessionStateType.ERROR
        return generateErrorSessionStateFromSessionEvent(errorMessage, sessionEvent, errorType, instant)
    }
}
