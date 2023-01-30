package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.session.manager.impl.processor.helper.recalcReceivedProcessState
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory
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

    private val sessionId = sessionEvent.sessionId

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun execute(): SessionState {
        return if (sessionState == null) {
            val errorMessage = "Received SessionClose on key $key and sessionId $sessionId  with null state"
            logger.debug { errorMessage }
            generateErrorSessionStateFromSessionEvent(errorMessage, sessionEvent, "SessionClose-NullSessionState", instant)
        } else {
            val seqNum = sessionEvent.sequenceNum
            val receivedEventsState = sessionState.receivedEventsState
            val lastProcessedSequenceNum = receivedEventsState.lastProcessedSequenceNum
            val undeliveredReceivedMessages = receivedEventsState.undeliveredMessages
            val sessionCloseOnQueue = undeliveredReceivedMessages.any { it.payload is SessionClose }
            if (sessionCloseOnQueue || sessionState.status == SessionStateType.CLOSED) {
                //duplicate
                logger.debug {
                    "Received duplicate SessionClose on key $key and sessionId $sessionId with seqNum of $seqNum " +
                            "when last processed seqNum was $lastProcessedSequenceNum. Current SessionState: $sessionState"
                }
                sessionState.apply {
                    sendAck = true
                }
            } else {
                sessionState.receivedEventsState.undeliveredMessages = undeliveredReceivedMessages.plus(sessionEvent)
                sessionState.receivedEventsState = recalcReceivedProcessState(receivedEventsState)
                processCloseReceivedAndGetState(sessionState)
            }
        }
    }

    private fun processCloseReceivedAndGetState(
        sessionState: SessionState
    ) = when (sessionState.status) {
        SessionStateType.CONFIRMED, SessionStateType.CREATED -> {
            sessionState.apply {
                logger.trace { "Updating session state to ${SessionStateType.CLOSING} for session state $sessionState" }
                status = SessionStateType.CLOSING
                sendAck = true
            }
        }
        SessionStateType.CLOSING -> {
            sessionState.apply {
                status = if (sendEventsState.undeliveredMessages.isNullOrEmpty()) {
                    logger.trace { "Updating session state to ${SessionStateType.CLOSED} for session state $sessionState" }
                    SessionStateType.CLOSED
                } else {
                    logger.trace { "Updating session state to ${SessionStateType.WAIT_FOR_FINAL_ACK} for session state $sessionState" }
                    SessionStateType.WAIT_FOR_FINAL_ACK
                }
                sendAck = true
            }
        }
        else -> {
            val errorMessage = "Received SessionClose on key $key and sessionId $sessionId when session status was " +
                    "${sessionState.status}. SessionState: $sessionState"
            logAndGenerateErrorResult(errorMessage, sessionState, "SessionClose-InvalidStatus")
        }
    }

    private fun logAndGenerateErrorResult(
        errorMessage: String,
        sessionState: SessionState,
        errorType: String
    ): SessionState {
        logger.warn(errorMessage)
        return sessionState.apply {
            status = SessionStateType.ERROR
            sendEventsState.undeliveredMessages =
                sendEventsState.undeliveredMessages.plus(generateErrorEvent(sessionState, sessionEvent, errorMessage, errorType, instant))

        }
    }
}
