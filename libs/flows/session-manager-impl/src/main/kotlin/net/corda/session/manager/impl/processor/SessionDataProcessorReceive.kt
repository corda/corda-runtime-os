package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateAckEvent
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.session.manager.impl.processor.helper.recalcReceivedProcessState
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.time.Instant

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
        private val logger = contextLogger()
    }

    override fun execute(): SessionState {
        val sessionId = sessionEvent.sessionId
        return if (sessionState == null) {
            val errorMessage = "Received SessionData on key $key for session which was null: SessionEvent: $sessionEvent"
            logger.error(errorMessage)
            generateErrorSessionStateFromSessionEvent(sessionId, errorMessage, "SessionData-NullSessionState", instant)
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

        return when {
            seqNum >= expectedNextSeqNum -> {
                getSessionStateForDataEvent(sessionState, sessionId, seqNum)
            }
            seqNum < expectedNextSeqNum -> {
                sessionState.apply {
                    sendEventsState.undeliveredMessages =
                        sessionState.sendEventsState.undeliveredMessages.plus(generateAckEvent(seqNum, sessionId, instant))
                }
            }
            else -> {
                logger.debug {
                    "Duplicate message on key $key with sessionId $sessionId with sequence number of $seqNum when next" +
                            " expected seqNum is $expectedNextSeqNum"
                }
                sessionState
            }
        }
    }

    private fun getSessionStateForDataEvent(
        sessionState: SessionState,
        sessionId: String,
        seqNum: Int
    ): SessionState {
        val receivedEventState = sessionState.receivedEventsState
        val currentStatus = sessionState.status

        //store data event received regardless of current state status
        receivedEventState.undeliveredMessages = receivedEventState.undeliveredMessages.plus(sessionEvent)

        //If in state of WAIT_FOR_FINAL_ACK/CLOSED we should not be receiving data messages with a valid seqNum
        return if (currentStatus == SessionStateType.WAIT_FOR_FINAL_ACK || currentStatus == SessionStateType.ERROR
            || currentStatus == SessionStateType.CLOSED) {
            val errorMessage ="Received data message on key $key with sessionId $sessionId with sequence number of $seqNum when status" +
                        " is $currentStatus. Session mismatch error. SessionState: $sessionState"
            logger.error(errorMessage)
            sessionState.apply {
                status = SessionStateType.ERROR
                sendEventsState.undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.plus(
                    generateErrorEvent(sessionId, errorMessage, "SessionData-SessionMismatch", instant)
                )
            }
        } else {
            sessionState.apply {
                receivedEventsState = recalcReceivedProcessState(receivedEventsState)
                sendEventsState.undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.plus(
                    generateAckEvent(seqNum, sessionId, instant)
                )
            }
        }
    }
}
