package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.isInitiatedIdentity
import net.corda.session.manager.Constants.Companion.FLOW_SESSION_REQUIRE_CLOSE
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Handle send of a [SessionClose] event.
 * If the state is null or ERROR send an error response to the counterparty as this indicates a bug in client code or  a
 * session mismatch has occurred.
 * If state is CLOSING or CLOSED, no update required.
 * If the client has not consumed all received events and it tries to send a close then trigger an error as this is a bug/session mismatch.
 * If the state is CONFIRMED then set the status to CLOSED
 */
class SessionCloseProcessorSend(
    private val key: Any,
    private val sessionState: SessionState,
    private val sessionEvent: SessionEvent,
    private val instant: Instant
) : SessionEventProcessor {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun execute(): SessionState {
        val sessionId = sessionEvent.sessionId
        val currentStatus = sessionState.status
        return when {
            currentStatus in setOf(SessionStateType.CLOSED, SessionStateType.CLOSING) -> {
                sessionState
            }
            currentStatus == SessionStateType.ERROR -> {
                handleInvalidStatus(sessionState)
            }
            hasUnprocessedReceivedDataEvents(sessionState) -> {
                handleUnprocessedReceivedDataEvents(sessionId, sessionState)
            }
            else -> {
                val nextSeqNum = sessionState.sendEventsState.lastProcessedSequenceNum + 1
                sessionEvent.sequenceNum = nextSeqNum
                getResultByCurrentState(sessionState, nextSeqNum)
            }
        }
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

    private fun getResultByCurrentState(
        sessionState: SessionState,
        nextSeqNum: Int,
    ) : SessionState {
        val sessionId = sessionState.sessionId
        val requireClose = sessionState.sessionProperties?.let {
            val sessionProperties = KeyValueStore(sessionState.sessionProperties)
            sessionProperties[FLOW_SESSION_REQUIRE_CLOSE].toBoolean()
        } ?: true

        return if (isInitiatedIdentity(sessionId) && sessionState.status !in listOf(SessionStateType.ERROR, SessionStateType.CLOSED)) {
            if (requireClose) {
                sessionState.apply {
                    logger.trace {
                        "Sending SessionClose and setting status to CLOSED. nextSeqNum: $nextSeqNum, adding this event to send " +
                                "${sessionEvent.sequenceNum}, $sessionId"
                    }
                    status = SessionStateType.CLOSED
                    sendEventsState.lastProcessedSequenceNum = nextSeqNum
                    sessionEvent.sequenceNum = nextSeqNum
                    sendEventsState.undeliveredMessages =
                        sessionState.sendEventsState.undeliveredMessages.plus(sessionEvent)
                }
            } else {
                sessionState.status = SessionStateType.CLOSED
                sessionState
            }
        } else {
            val status = sessionState.status
            val errorMessage =
                "Tried to send SessionClose. " +
                        "Check party is initiated: ${isInitiatedIdentity(sessionId)}" +
                        "Check status is not ERROR or CLOSED, status is: $status." +
                        "Key: $key SessionId: $sessionId current SessionState: $sessionState."
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
