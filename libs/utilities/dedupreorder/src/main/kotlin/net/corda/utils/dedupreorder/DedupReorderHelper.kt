package net.corda.utils.dedupreorder

interface DedupReorderHelper<S, E> {

    /**
     * Get the sequence number for an [event]
     */
    fun getEventSequenceNumber(event: E): Int

    /**
     * Get the buffered out of order events from a [state]
     */
    fun getOutOfOrderMessages(state: S?): MutableList<E>

    /**
     * Get the sequence number of the last successful event from the [state]
     */
    fun getCurrentSequenceNumber(state: S?): Int

    /**
     * Update the [oldState] with the new [newStateSequenceNumber] and set the [newOutOfOrderMessages]
     */
    fun updateState(
        oldState: S?,
        newStateSequenceNumber: Int,
        newOutOfOrderMessages: MutableList<E>
    ): S?
}