package net.corda.utils.dedupreorder

interface DedupReorderHelper<S, E> {
    fun getEventTimestampField(event: E): Long
    fun getEventSequenceNumber(event: E): Int
    fun getOutOfOrderMessages(state: S?): MutableList<E>
    fun getCurrentSequenceNumber(state: S?): Int
    fun updateState(
        oldState: S?,
        newStateSequenceNumber: Int,
        newOutOfOrderMessages: MutableList<E>
    ): S?
}