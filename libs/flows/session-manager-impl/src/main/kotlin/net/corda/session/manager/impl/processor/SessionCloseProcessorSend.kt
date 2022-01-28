package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
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
                val errorMessage = "Tried to send SessionClose with flow key $key and sessionId $sessionId  with null state"
                logger.error(errorMessage)
                generateErrorSessionStateFromSessionEvent(sessionId, errorMessage, "SessionCLose-StateNull", instant)
            }
            currentStatus == SessionStateType.ERROR -> {
                val errorMessage = "Tried to send SessionClose on key $key for sessionId $sessionId with status of : $currentStatus"
                logger.error(errorMessage)
                sessionState.apply {
                    sendEventsState.undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.plus(
                        generateErrorEvent(sessionId, errorMessage, "SessionClose-InvalidStatus", instant)
                    )
                }
            }
            //session mismatch error
            sessionState.receivedEventsState.undeliveredMessages.isNotEmpty() -> {
                val errorMessage = "Tried to send SessionClose on key $key and sessionId $sessionId, session status is " +
                        "${sessionState.status}, however there are still received events that have not been processed. " +
                        "Current SessionState: $sessionState. Sending error to counterparty"
                logAndGenerateErrorResult(errorMessage, sessionState, sessionId, "SessionClose-SessionMismatch")
            }
            else -> {
                val nextSeqNum = sessionState.sendEventsState.lastProcessedSequenceNum + 1
                sessionEvent.apply {
                    sequenceNum = nextSeqNum
                    timestamp = instant.toEpochMilli()
                }

                getResultByCurrentState(sessionState, sessionId, nextSeqNum)
            }
        }
    }

    private fun getResultByCurrentState(
        sessionState: SessionState,
        sessionId: String,
        nextSeqNum: Int,
    ) = when (val currentState = sessionState.status) {
        SessionStateType.CREATED -> {
            sessionState.apply {
                status = SessionStateType.CLOSING
                sendEventsState.lastProcessedSequenceNum = nextSeqNum
                sendEventsState.undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.plus(sessionEvent)
            }
        }
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
        SessionStateType.CLOSED -> {
            //session is already completed successfully. Indicates bug. should we send an error back and change state to error
            logger.error(
                "Tried to send SessionClose on key $key and sessionId $sessionId, session status is " +
                        "$currentState. Current SessionState: $sessionState"
            )
            sessionState
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
    ): SessionState {
        logger.error(errorMessage)
        sessionState.status = SessionStateType.ERROR
        return generateErrorSessionStateFromSessionEvent(sessionId, errorMessage, errorType, instant)
    }
}
