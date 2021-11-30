package net.corda.session.reorder

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace

/**
 * Helper class to deduplicate and reorder session events based on sequence numbers and a received events [SessionProcessState] object.
 * Requires the client library to signal back to the [ReorderHandler] when it has successfully processed the events returned by
 * [processEvent]/[getNextEventsInSequence] methods the [signalDeliveredEvents] call.
 */
class ReorderHandler {

    companion object {
        private val log = contextLogger()
    }

    /**
     * Process an [event] and [state]. Checks the [state] for buffered events and returns a list of events in a contiguous sequence that
     * have not been marked as delivered by the client.
     * Updates the [state] to store the sequence number of the last event ready to be delivered to the client library.
     */
    fun processEvent(state: SessionProcessState?, event: SessionEvent): Pair<SessionProcessState?, List<SessionEvent>> {
        log.trace { "Received event $event with state $state" }
        val nextEvents = mutableListOf<SessionEvent>()
        var responseState = state

        val eventSequenceNumber = event.sequenceNum

        if (state == null) {
            if (eventSequenceNumber == 1) {
                nextEvents.add(event)
                responseState = SessionProcessState(1, mutableListOf(event))
            } else {
                log.debug { "Dropping Message. Event with sequence number $eventSequenceNumber for null state" }
            }
        } else {
            val undeliveredMessages = state.undeliveredMessages?.toMutableList() ?: mutableListOf()
            val expectedNextSeqNum = state.lastProcessedSequenceNum + 1
            if (eventSequenceNumber >= expectedNextSeqNum) {
                undeliveredMessages.add(event)
                val (updatedState, outputEvents) = getNextMessagesAndSeqNum(expectedNextSeqNum, undeliveredMessages)
                nextEvents.addAll(outputEvents)
                responseState = updatedState
            } else {
                log.debug { "Duplicate message with sequence number of $eventSequenceNumber" }
            }
        }

        log.debug { "Returning state $responseState with output record count: ${nextEvents.size}" }
        return Pair(responseState, nextEvents)
    }

    /**
     * Sort the list of buffered [undeliveredMessages] and return the list of next messages based on the [expectedNextSeqNum].
     * @return the state for the received events containing the sequence number of the last contiguous event in the sequence of delivered
     * and undelivered messages, as well as the current list next messages available in the sequence.
     */
    private fun getNextMessagesAndSeqNum(
        expectedNextSeqNum: Int,
        undeliveredMessages: MutableList<SessionEvent>
    ): Pair<SessionProcessState, List<SessionEvent>> {
        val nextRecords = mutableListOf<SessionEvent>()
        var nextSeqNum = expectedNextSeqNum
        val orderedUniqueMessages = undeliveredMessages.distinctBy { it.sequenceNum }.sortedBy { it.sequenceNum }

        for (undeliveredMessage in orderedUniqueMessages) {
            if (undeliveredMessage.sequenceNum == nextSeqNum) {
                nextRecords.add(undeliveredMessage)
                nextSeqNum++
            } else {
                break
            }
        }

        return Pair(SessionProcessState(nextSeqNum - 1, orderedUniqueMessages), nextRecords)
    }

    /**
     * Get a list of session events available to be processed in a contiguous sequence from the events [state].
     * Events are sorted by their sequence number.
     */
    fun getNextEventsInSequence(state: SessionProcessState): List<SessionEvent> {
        return state.undeliveredMessages.sortedBy { it.sequenceNum }.filter { it.sequenceNum <= state.lastProcessedSequenceNum }
    }

    /**
     * Update the [state] to sginal that events have been processed up to the [lastDeliveredSequenceNum].
     * @throws CordaRuntimeException when the [lastDeliveredSequenceNum] is not an event in the current contiguous sequence of next events.
     */
    fun signalDeliveredEvents(state: SessionProcessState, lastDeliveredSequenceNum: Int): SessionProcessState {
        if (lastDeliveredSequenceNum > state.lastProcessedSequenceNum) {
            //TODO - figure out better exception type
            throw CordaRuntimeException("Tried to signal events processed up to $lastDeliveredSequenceNum, but events only available up " +
                    "to sequenceNum of ${state.lastProcessedSequenceNum}")
        }
        state.undeliveredMessages =
            state.undeliveredMessages.sortedBy { it.sequenceNum }.filter { it.sequenceNum > lastDeliveredSequenceNum }
        return state
    }
}
