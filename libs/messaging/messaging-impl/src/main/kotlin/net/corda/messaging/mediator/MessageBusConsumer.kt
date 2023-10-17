package net.corda.messaging.mediator

import java.time.Duration
import net.corda.messagebus.api.CordaOffsetAndMetadata
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messaging.api.mediator.MediatorConsumer

/**
 * Message bus consumer that reads messages from configured topic.
 */
class MessageBusConsumer<K: Any, V: Any>(
    private val topic: String,
    private val consumer: CordaConsumer<K, V>,
): MediatorConsumer<K, V> {
    private val revokedPartitions = mutableSetOf<CordaTopicPartition>()

    override fun subscribe() = consumer.subscribe(topic, object : CordaConsumerRebalanceListener {
        override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
            revokedPartitions.clear()
        }

        override fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>) {
            revokedPartitions.addAll(partitions)
        }
    })

    override fun poll(timeout: Duration): List<CordaConsumerRecord<K, V>> = consumer.poll(timeout)

    override fun syncCommitOffsets() {
        val offsetsToCommit = consumer.assignment()
            .filter { it !in revokedPartitions }
            .associateWith { CordaOffsetAndMetadata(consumer.position(it)) }

        consumer.syncCommitOffsets(offsetsToCommit)
    }

    override fun resetEventOffsetPosition() =
        consumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)

    override fun close() = consumer.close()
}