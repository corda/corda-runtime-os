package net.corda.messaging.api.mediator

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * Multi-source event mediator message consumer.
 */
interface MediatorConsumer<K : Any, V : Any> : AutoCloseable {

    /**
     * Subscribes to a message bus.
     */
    fun subscribe()

    /**
     * Poll messages from the consumer with a [timeout].
     *
     * @param timeout - The maximum time to block if there are no available messages.
     */
    fun poll(timeout: Duration): List<CordaConsumerRecord<K, V>>

    /**
     * Asynchronously commit the consumer offsets.
     *
     * @return [CompletableFuture] with committed offsets.
     */
    fun commitAsync(): CompletableFuture<Map<CordaTopicPartition, Long>>
}
