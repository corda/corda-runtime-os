package net.corda.utils.dedupreorder

import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

/**
 * Helper Class to deduplicate and reorder messages based on sequence numbers.
 * Utilizes a [dedupReorderHelper] to inform read sequence numbers from events/state and to update the state with new
 * sequence numbers.
 */
class DedupReorderUtils<S : Any, E : Any>(
    private val dedupReorderHelper: DedupReorderHelper<S, E>
) {

    companion object {
        private val log = contextLogger()
    }

    /**
     * Check an [event] to see if it is an old message, duplicate or out of order message.
     * Uses the [dedupReorderHelper] to extract sequence numbers from [state] and [event] objects.
     * If the event sequence number is lower than the next expected event it is dropped and an empty list is returned.
     * If the event sequence number is higher than expected and the missing messages are not buffered to state the event is saved to the
     * state.
     * If the event sequence number is that of the next expected event, then the event is returned as well as any of the following buffered
     * messages in the sequence.
     * Also returns the state updated by the [dedupReorderHelper]
     */
    fun getNextEvents(state: S?, event: E?): Pair<S?, List<E?>> {
        log.debug { "Received event $event with state $state" }
        val eventValue = event ?: return Pair(state, listOf(event))

        val nextEvents = mutableListOf<E>()
        var responseState: S? = state

        val eventSequenceNumber = dedupReorderHelper.getEventSequenceNumber(eventValue)
        val outOfOrderMessages = dedupReorderHelper.getOutOfOrderMessages(state)

        if (state == null) {
            if (eventSequenceNumber == 1) {
                nextEvents.add(event)
                responseState = dedupReorderHelper.updateState(
                    state,
                    1,
                    mutableListOf()
                )
            }
            else {
                log.debug { "Dropping Message. Event with sequence number $eventSequenceNumber for null state" }
            }
        } else {
            val expectedNextSeqNum = dedupReorderHelper.getCurrentSequenceNumber(state) + 1
            if (eventSequenceNumber >= expectedNextSeqNum){
                val (newStateSequenceNumber, outputEvents) = getNextMessagesAndSeqNum(expectedNextSeqNum, outOfOrderMessages, eventValue)
                nextEvents.addAll(outputEvents)

                responseState = dedupReorderHelper.updateState(
                    state,
                    newStateSequenceNumber,
                    outOfOrderMessages
                )
            }
            else {
                log.debug { "Duplicate message with sequence number of $eventSequenceNumber" }
            }
        }

        log.debug { "Returning state $responseState with output record count: ${nextEvents.size}" }
        return Pair(responseState, nextEvents)
    }

    /**
     * Sort the list of buffered messages and return the list of next messages based on the [expectedNextSeqNum] and the new sequence
     * number for the state.
     * Remove any output messages from [stateOutOfOrderMessages].
     * Add the [eventValue] to the the [stateOutOfOrderMessages] if it is not the next in the expected sequence.
     */
    private fun getNextMessagesAndSeqNum(expectedNextSeqNum: Int, stateOutOfOrderMessages: MutableList<E>, eventValue: E):
            Pair<Int, List<E>> {
        val nextRecords = mutableListOf<E>()
        var nextSeqNum = expectedNextSeqNum
        val newEventSeqNum = dedupReorderHelper.getEventSequenceNumber(eventValue)

        if (newEventSeqNum == nextSeqNum) {
            nextRecords.add(eventValue)
            nextSeqNum++

            for (outOfOrderMessage in stateOutOfOrderMessages.sortedBy { dedupReorderHelper.getEventSequenceNumber(it) }) {
                if (dedupReorderHelper.getEventSequenceNumber(outOfOrderMessage) == nextSeqNum) {
                    nextRecords.add(outOfOrderMessage)
                    stateOutOfOrderMessages.remove(outOfOrderMessage)
                    nextSeqNum++
                }
                else {
                    break
                }
            }
        } else {
            stateOutOfOrderMessages.add(eventValue)
        }

        return Pair(nextSeqNum - 1, nextRecords)
    }
}
