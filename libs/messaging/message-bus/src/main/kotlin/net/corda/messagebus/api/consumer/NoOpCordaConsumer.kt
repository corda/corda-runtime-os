package net.corda.messagebus.api.consumer

import net.corda.messagebus.api.CordaTopicPartition
import java.time.Duration


/**
 * CordaConsumers connect to and receive messages from the message bus.
 */
@Suppress("TooManyFunctions")
class NoOpCordaConsumer<K : Any, V : Any>(
) : CordaConsumer<K, V> {
    override fun subscribe(topics: Collection<String>, listener: CordaConsumerRebalanceListener?) {
        // NoOp Implementation
    }

    override fun subscribe(topic: String, listener: CordaConsumerRebalanceListener?) {
        // NoOp Implementation
    }

    override fun assign(partitions: Collection<CordaTopicPartition>) {
        // NoOp Implementation
    }

    override fun assignment(): Set<CordaTopicPartition> {
        // NoOp Implementation
        return setOf()
    }

    override fun position(partition: CordaTopicPartition): Long {
        return 0;
    }

    override fun seek(partition: CordaTopicPartition, offset: Long) {
        // NoOp Implementation
    }

    override fun seekToBeginning(partitions: Collection<CordaTopicPartition>) {
        // NoOp Implementation
    }

    override fun seekToEnd(partitions: Collection<CordaTopicPartition>) {
        // NoOp Implementation
    }

    override fun beginningOffsets(partitions: Collection<CordaTopicPartition>): Map<CordaTopicPartition, Long> {
        return mapOf()
    }

    override fun endOffsets(partitions: Collection<CordaTopicPartition>): Map<CordaTopicPartition, Long> {
        return mapOf()
    }

    override fun resume(partitions: Collection<CordaTopicPartition>) {
        // NoOp Implementation
    }

    override fun pause(partitions: Collection<CordaTopicPartition>) {
        // NoOp Implementation
    }

    override fun paused(): Set<CordaTopicPartition> {
        return setOf()
    }

    override fun poll(timeout: Duration): List<CordaConsumerRecord<K, V>> {
        return listOf()
    }

    override fun resetToLastCommittedPositions(offsetStrategy: CordaOffsetResetStrategy) {
        // NoOp Implementation
    }

    override fun getPartitions(topic: String): List<CordaTopicPartition> {
        return listOf()
    }

    override fun setDefaultRebalanceListener(defaultListener: CordaConsumerRebalanceListener) {
        // NoOp Implementation
    }

    override fun close() {
        // NoOp Implementation
    }

    override fun commitSyncOffsets(event: CordaConsumerRecord<K, V>, metaData: String?) {
        // NoOp Implementation
    }

}
