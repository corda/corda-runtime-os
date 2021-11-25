package net.corda.utils.dedupreorder

import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

/**
 * Helper Class to deduplicate and reorder messages based on sequence numbers.
 * Utilizes a [dedupReorderHelper] to inform read sequence numbers and timestamps from events/state and to update the state with new
 * sequence numbers.
 */
class DedupReorderUtils<S : Any, E : Any>(
    private val messageValidityWindow: Long,
    private val dedupReorderHelper: DedupReorderHelper<S, E>
) {

    companion object {
        private val log = contextLogger()
    }

    /**
     * Check an [event] to see if it is an old message, duplicate or out of order message.
     * Uses the [dedupReorderHelper] to extract sequence numbers and timestamps from [state] and [event] objects.
     * If the event sequence number of the event is lower than the next expected event it is dropped and an empty list is returned.
     * If the event sequence number is higher than expected and the missing messages are not buffered to state the event is saved to the
     * state.
     * If the event sequence number is that of the next expected event, then the event is returned as well as any of the following buffered
     * messages in the sequence.
     * Also returns the state updated by the [dedupReorderHelper]
     */
    fun getNextEvents(state: S?, event: E?): Pair<S?, List<E?>> {
        log.info("Received event $event with state $state")
        val eventValue = event ?: return Pair(state, listOf(event))

        val nextRecords = mutableListOf<E>()
        var responseState: S? = state

        val eventTimestamp = dedupReorderHelper.getEventTimestampField(eventValue)
        val eventSequenceNumber = dedupReorderHelper.getEventSequenceNumber(eventValue)
        val eventExpiryTime = eventTimestamp + messageValidityWindow
        val outOfOrderMessages = dedupReorderHelper.getOutOfOrderMessages(state)
        val currentTime = System.currentTimeMillis()

        if (state == null) {
            log.debug { "State is null" }
            if (eventSequenceNumber == 1 && eventExpiryTime > currentTime) {
                nextRecords.add(event)
                responseState = dedupReorderHelper.updateState(
                    state,
                    1,
                    mutableListOf()
                )
            }
            else {
                log.info("Dropping Message. Event with expiry time $eventExpiryTime, sequence number $eventSequenceNumber")
            }
        } else {
            val expectedNextSeqNum = dedupReorderHelper.getCurrentSequenceNumber(state) + 1
            if (eventSequenceNumber >= expectedNextSeqNum){
                outOfOrderMessages.add(eventValue)
                val (newStateSequenceNumber, nextEvents) = getNextMessagesAndSeqNum(expectedNextSeqNum, outOfOrderMessages)
                nextRecords.addAll(nextEvents)

                responseState = dedupReorderHelper.updateState(
                    state,
                    newStateSequenceNumber,
                    outOfOrderMessages
                )
            }
            else {
                log.info("Duplicate message with sequence number of $eventSequenceNumber")
            }
        }

        return Pair(responseState, nextRecords)
    }

    private fun getNextMessagesAndSeqNum(expectedNextSeqNum: Int, stateOutOfOrderMessages: MutableList<E>):
            Pair<Int, List<E>> {
        val nextRecords = mutableListOf<E>()
        var nextSeqNum = expectedNextSeqNum
        val orderedNoDuplicates = stateOutOfOrderMessages.sortedBy { dedupReorderHelper.getEventSequenceNumber(it) }.distinctBy {
            dedupReorderHelper.getEventSequenceNumber(it)
        }

        for (outOfOrderMessage in orderedNoDuplicates) {
            if (dedupReorderHelper.getEventSequenceNumber(outOfOrderMessage) == nextSeqNum) {
                nextRecords.add(outOfOrderMessage)
                stateOutOfOrderMessages.remove(outOfOrderMessage)
                nextSeqNum++
            } else {
                break
            }
        }
        return Pair(nextSeqNum - 1, nextRecords)
    }
}
