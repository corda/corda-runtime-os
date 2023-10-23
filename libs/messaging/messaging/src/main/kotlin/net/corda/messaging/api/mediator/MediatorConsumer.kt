package net.corda.messaging.api.mediator

import net.corda.messagebus.api.consumer.CordaConsumerRecord

/**
 * Multi-source event mediator message consumer.
 */
interface MediatorConsumer<K : Any, V : Any> : AutoCloseable {

    /**
     * Subscribes to a message bus.
     */
    fun subscribe()

    fun setDataAvailableCallback(callback: () -> Unit) {

    }

    /**
     * Poll messages from the consumer.
     */
    fun poll(): List<CordaConsumerRecord<K, V>>

    /**
     * Synchronously commits the consumer offsets. This function should be called only after `poll` was called.
     */
    fun syncCommitOffsets()

    /**
     * Resets consumer's offsets to the last committed positions. Next poll will read from the last committed positions.
     */
    fun resetEventOffsetPosition()
}
