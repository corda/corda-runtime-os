package net.corda.messaging.mediator

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messaging.api.mediator.MediatorConsumer
import java.time.Duration

/**
 * Message bus consumer that reads messages from configured topic.
 */
class MessageBusConsumer<K: Any, V: Any>(
    private val topic: String,
    private val consumer: CordaConsumer<K, V>,
): MediatorConsumer<K, V> {

    override fun subscribe() =
        consumer.subscribe(topic)

    override fun poll(timeout: Duration): Deferred<List<CordaConsumerRecord<K, V>>> =
        CompletableDeferred<List<CordaConsumerRecord<K, V>>>().apply {
            try {
                complete(consumer.poll(timeout))
            } catch (throwable: Throwable) {
                completeExceptionally(throwable)
            }
        }

    override fun asyncCommitOffsets(): Deferred<Map<CordaTopicPartition, Long>> =
        CompletableDeferred<Map<CordaTopicPartition, Long>>().apply {
            consumer.asyncCommitOffsets { offsets, exception ->
                if (exception != null) {
                    completeExceptionally(exception)
                } else {
                    complete(offsets)
                }
            }
        }

    override fun resetEventOffsetPosition() =
        consumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)

    override fun close() =
        consumer.close()
}