package net.corda.messaging.mediator

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messaging.api.mediator.MediatorConsumer
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * Message bus consumer that reads messages from configured topic.
 */
class MessageBusConsumer<K: Any, V: Any>(
    private val topic: String,
    private val consumer: CordaConsumer<K, V>,
): MediatorConsumer<K, V> {

    override fun subscribe() =
        consumer.subscribe(topic)

    override fun poll(timeout: Duration): List<CordaConsumerRecord<K, V>> =
        consumer.poll(timeout)

    override fun commitAsync(): CompletableFuture<Map<CordaTopicPartition, Long>> {
        val result = CompletableFuture<Map<CordaTopicPartition, Long>>()
        consumer.commitAsync { offsets, exception ->
            if (exception != null) {
                result.completeExceptionally(exception)
            } else {
                result.complete(offsets)
            }
        }
        return result
    }

    override fun resetEventOffsetPosition() =
        consumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)

    override fun close() =
        consumer.close()
}