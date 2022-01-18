package net.corda.session.manager.impl.processor

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionProcessState
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
 * Process a [SessionData] event.
 * Deduplicate and reorder the event according to its sequence number.
 * If the current state is ERROR return an error message to the counterparty.
 * If the current state is CLOSED and the event is not a duplicate then return an error to the counterparty as there is mismatch of
 * events and the state is out of sync. This likely indicates a bug in the client code.
 * If the event is a duplicate log it.
 * Otherwise buffer the event in the received events state.
 */
class SessionDataProcessor(
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
        val messageDirection = sessionEvent.messageDirection

        if (sessionState == null) {
            val errorMessage = "Received SessionData on key $flowKey for sessionId which was null: $sessionId"
            logger.error(errorMessage)
            return SessionEventResult(sessionState, generateErrorEvent(sessionId, errorMessage, "SessionData-NullSessionState", instant))
        }

        return if (messageDirection == MessageDirection.OUTBOUND) {
            getOutboundDataEventResult(sessionState, sessionId)
        } else {
            getInboundDataEventResult(sessionState, sessionId)
        }
    }

    private fun getOutboundDataEventResult(
        sessionState: SessionState,
        sessionId: String
    ): SessionEventResult {
        val currentStatus = sessionState.status

        if (currentStatus != SessionStateType.CONFIRMED) {
            val errorMessage = "Received SessionData on key $flowKey for sessionId with status of : $currentStatus"
            logger.error(errorMessage)
            return SessionEventResult(sessionState, generateErrorEvent(sessionId, errorMessage, "SessionData-InvalidStatus", instant))
        }

        val sentEventState = sessionState.sentEventsState
        val nextSeqNum = sentEventState.lastProcessedSequenceNum + 1
        val undeliveredMessages = sentEventState.undeliveredMessages?.toMutableList() ?: mutableListOf()
        sessionEvent.sequenceNum = nextSeqNum
        undeliveredMessages.add(sessionEvent)
        return SessionEventResult(sessionState, generateOutBoundRecord(sessionEvent, sessionId, instant))
    }

    private fun getInboundDataEventResult(
        sessionState: SessionState,
        sessionId: String
    ): SessionEventResult {
        val seqNum = sessionEvent.sequenceNum
        val receivedEventState = sessionState.receivedEventsState
        val expectedNextSeqNum = receivedEventState.lastProcessedSequenceNum + 1
        val currentStatus = sessionState.status
        val undeliveredMessages = receivedEventState.undeliveredMessages?.toMutableList() ?: mutableListOf()

        return when {
            seqNum >= expectedNextSeqNum -> {
                if (currentStatus != SessionStateType.CONFIRMED) {
                    val errorMessage =
                        "Data message on flowKey $flowKey with sessionId $sessionId with sequence number of $seqNum when status" +
                                " is $currentStatus. SessionState: $sessionState"
                    //Possibly only Warn here as if we are in error state we have already sent counterparty an error and this data message was
                    // sent before they have processed that error
                    logger.error(errorMessage)
                    SessionEventResult(sessionState, generateErrorEvent(sessionId, errorMessage, "SessionData-InvalidStatus", instant))
                } else {
                    undeliveredMessages.add(sessionEvent)
                    sessionState.receivedEventsState = updateSessionProcessState(expectedNextSeqNum + 1, undeliveredMessages)
                    SessionEventResult(sessionState, generateAckRecord(seqNum, sessionId, instant))
                }
            }
            else -> {
                logger.debug {
                    "Duplicate message on flowKey $flowKey with sessionId $sessionId with sequence number of $seqNum when next" +
                            " expected seqNum is $expectedNextSeqNum"
                }
                SessionEventResult(sessionState, null)
            }
        }
    }

    /**
     * Update and return the session state. Set the last processed sequence number of the received events state to the last contiguous event
     * in the sequence of [undeliveredMessages].
     */
    private fun updateSessionProcessState(
        expectedNextSeqNum: Int,
        undeliveredMessages: MutableList<SessionEvent>
    ): SessionProcessState {
        var nextSeqNum = expectedNextSeqNum
        val sortedEvents = undeliveredMessages.distinctBy { it.sequenceNum }.sortedBy { it.sequenceNum }
        for (undeliveredMessage in sortedEvents) {
            if (undeliveredMessage.sequenceNum == nextSeqNum) {
                nextSeqNum++
            } else {
                break
            }
        }

        return SessionProcessState(nextSeqNum - 1, sortedEvents)
    }
}
