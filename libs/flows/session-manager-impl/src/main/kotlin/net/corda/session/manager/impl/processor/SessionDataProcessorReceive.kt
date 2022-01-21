package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.SessionEventResult
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateAckEvent
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.time.Instant

/**
 * Process a [SessionData] event received from a counterparty.
 * Deduplicate and reorder the event according to its sequence number.
 * If the current state is not CONFIRMED and the event is not a duplicate then return an error to the counterparty as there is
 * mismatch of events and the state is out of sync. This likely indicates a bug in the client code.
 * If the event is a duplicate log it.
 * Otherwise buffer the event in the received events state ready to be processed by the client code via the session manager api.
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

    override fun execute(): SessionEventResult {
        val sessionId = sessionEvent.sessionId
        return if (sessionState == null) {
            val errorMessage = "Received SessionData on key $key for session which was null: SessionEvent: $sessionEvent"
            logger.error(errorMessage)
            SessionEventResult(
                sessionState,
                listOf(generateErrorEvent(sessionId, errorMessage, "SessionData-NullSessionState", instant))
            )
        } else {
            getInboundDataEventResult(sessionState, sessionId)
        }
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
                undeliveredMessages.add(sessionEvent)
                
                if (currentStatus != SessionStateType.CONFIRMED || currentStatus != SessionStateType.CREATED) {
                    val errorMessage =
                        "Data message on key $key with sessionId $sessionId with sequence number of $seqNum when status" +
                                " is $currentStatus. SessionState: $sessionState"
                    //Possibly only Warn here as if we are in error state we have already sent counterparty an error and this data message
                    // was likely sent before they have processed that error.
                    // note: It should be not possible to be in a state of CREATED as
                    logger.error(errorMessage)
                    SessionEventResult(
                        sessionState, listOf(
                            generateErrorEvent(
                                sessionId, errorMessage, "SessionData-InvalidStatus",
                                instant
                            )
                        )
                    )
                } else {
                    sessionState.receivedEventsState = updateSessionProcessState(expectedNextSeqNum, undeliveredMessages)
                    SessionEventResult(sessionState, listOf(generateAckEvent(seqNum, sessionId, instant)))
                }
            }
            else -> {
                logger.debug {
                    "Duplicate message on key $key with sessionId $sessionId with sequence number of $seqNum when next" +
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
