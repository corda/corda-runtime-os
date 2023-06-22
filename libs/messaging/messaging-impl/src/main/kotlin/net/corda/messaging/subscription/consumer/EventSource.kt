package net.corda.messaging.subscription.consumer

interface EventSource<K : Any, E : Any> : AutoCloseable {
    fun start(nextOffsetSupplier: (List<Int>) -> Map<Int, Long>, offsetFilter: (Int, Long) -> Boolean)
    fun nextBlock(maxBatchSize: Int): List<EventSourceRecord<K, E>>
}

