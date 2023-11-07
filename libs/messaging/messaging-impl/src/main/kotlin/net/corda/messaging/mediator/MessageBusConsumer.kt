package net.corda.messaging.mediator

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
    override fun subscribe() = consumer.subscribe(topic)

    override fun poll(timeout: Duration): List<CordaConsumerRecord<K, V>> = consumer.poll(timeout)

    override fun syncCommitOffsets() = consumer.syncCommitOffsets()

    override fun resetEventOffsetPosition() =
        consumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)

    override fun close() = consumer.close()
}