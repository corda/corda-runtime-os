package net.corda.session.manager.impl.processor

import java.time.Instant
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.session.manager.impl.processor.helper.recalcReceivedProcessState
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory

/**
 * Process a [SessionData] event received from a counterparty.
 * Deduplicate and reorder the event according to its sequence number.
 * If the current state is not CONFIRMED or CREATED and the event is not a duplicate then return an error to the counterparty as there is
 * mismatch of events and the state is out of sync. This likely indicates a bug in the client code.
 * If the event is a duplicate log it and queue a SessionAck to be sent.
 * Otherwise buffer the event in the received events state ready to be processed by the client code via the session manager api and queue
 * a session ack to send.
 */
class SessionDataProcessorReceive(
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
        return if (sessionState == null) {
            val errorMessage = "Received SessionData on key $key for session which was null"
            logger.debug { errorMessage }
            generateErrorSessionStateFromSessionEvent(errorMessage, sessionEvent, "SessionData-NullSessionState", instant)
        } else {
            getInboundDataEventResult(sessionState, sessionId)
        }
    }

    private fun getInboundDataEventResult(
        sessionState: SessionState,
        sessionId: String
    ): SessionState {
        val seqNum = sessionEvent.sequenceNum
        val receivedEventState = sessionState.receivedEventsState
        val expectedNextSeqNum = receivedEventState.lastProcessedSequenceNum + 1

        return if (seqNum >= expectedNextSeqNum) {
            getSessionStateForDataEvent(sessionState, sessionId, seqNum, expectedNextSeqNum)
        } else {
            logger.debug {
                "Duplicate message received on key $key with sessionId $sessionId with sequence number of $seqNum when next" +
                        " expected seqNum is $expectedNextSeqNum"
            }
            sessionState.apply {
                sendAck = true
            }
        }
    }

    private fun getSessionStateForDataEvent(
        sessionState: SessionState,
        sessionId: String,
        seqNum: Int,
        expectedNextSeqNum: Int
    ): SessionState {
        val receivedEventState = sessionState.receivedEventsState
        val currentStatus = sessionState.status

        //store data event received regardless of current state status
        receivedEventState.undeliveredMessages = receivedEventState.undeliveredMessages.plus(sessionEvent)

        return if (isSessionMismatch(receivedEventState, expectedNextSeqNum, currentStatus)) {
            val errorMessage = "Received data message on key $key with sessionId $sessionId with sequence number of $seqNum when status" +
                    " is $currentStatus. Session mismatch error. SessionState: $sessionState"
            logger.warn(errorMessage)
            sessionState.apply {
                status = SessionStateType.ERROR
                sendEventsState.undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.plus(
                    generateErrorEvent(sessionState, sessionEvent, errorMessage, "SessionData-SessionMismatch", instant)
                )
            }
        } else {
            sessionState.apply {
                receivedEventsState = recalcReceivedProcessState(receivedEventsState)
                sendAck = true
            }
            logger.trace { "receivedEventsState after update: ${sessionState.receivedEventsState}" }
            sessionState
        }
    }

    /**
     * If the session is the wrong state or a new data message is received after close was already received then there has been a session
     * mismatch error.
     * If in state of WAIT_FOR_FINAL_ACK/ERROR/CLOSED we should not be receiving data messages with a valid seqNum or if its a new
     * data message when the session is closing. Out of order data messages are allowed if they have seqNum lower than the close received.
     * @param receivedEventState Received events state.
     * @param expectedNextSeqNum the next sequence number this party is expecting to receive
     * @param currentStatus To validate the state is valid to receive new messages
     * @return True if there is a mismatch of messages between parties, false otherwise.
     */
    private fun isSessionMismatch(receivedEventState: SessionProcessState, expectedNextSeqNum: Int, currentStatus: SessionStateType) :
            Boolean {
        val receivedCloseSeqNum = receivedEventState.undeliveredMessages.find { it.payload is SessionClose }?.sequenceNum
        val otherPartyClosingMismatch = receivedCloseSeqNum != null && receivedCloseSeqNum <= expectedNextSeqNum
        val thisPartyClosingMismatch = receivedCloseSeqNum == null && currentStatus == SessionStateType.CLOSING
        val statusMismatch = currentStatus == SessionStateType.WAIT_FOR_FINAL_ACK || currentStatus == SessionStateType.ERROR
                || currentStatus == SessionStateType.CLOSED
        return otherPartyClosingMismatch || thisPartyClosingMismatch || statusMismatch
    }
}
