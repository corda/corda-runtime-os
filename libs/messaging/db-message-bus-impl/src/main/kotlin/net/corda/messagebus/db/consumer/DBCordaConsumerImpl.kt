package net.corda.messagebus.db.consumer

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import java.time.Duration

@Suppress("TooManyFunctions")
class DBCordaConsumerImpl<K : Any, V: Any>: CordaConsumer<K, V> {
    override fun subscribe(topics: Collection<String>, listener: CordaConsumerRebalanceListener?) {
        TODO("Not yet implemented")
    }

    override fun subscribe(topic: String, listener: CordaConsumerRebalanceListener?) {
        TODO("Not yet implemented")
    }

    override fun assign(partitions: Collection<CordaTopicPartition>) {
        TODO("Not yet implemented")
    }

    override fun assignment(): Set<CordaTopicPartition> {
        TODO("Not yet implemented")
    }

    override fun position(partition: CordaTopicPartition): Long {
        TODO("Not yet implemented")
    }

    override fun seek(partition: CordaTopicPartition, offset: Long) {
        TODO("Not yet implemented")
    }

    override fun seekToBeginning(partitions: Collection<CordaTopicPartition>) {
        TODO("Not yet implemented")
    }

    override fun seekToEnd(partitions: Collection<CordaTopicPartition>) {
        TODO("Not yet implemented")
    }

    override fun beginningOffsets(partitions: Collection<CordaTopicPartition>): Map<CordaTopicPartition, Long> {
        TODO("Not yet implemented")
    }

    override fun endOffsets(partitions: Collection<CordaTopicPartition>): Map<CordaTopicPartition, Long> {
        TODO("Not yet implemented")
    }

    override fun resume(partitions: Collection<CordaTopicPartition>) {
        TODO("Not yet implemented")
    }

    override fun pause(partitions: Collection<CordaTopicPartition>) {
        TODO("Not yet implemented")
    }

    override fun paused(): Set<CordaTopicPartition> {
        TODO("Not yet implemented")
    }

    override fun poll(timeout: Duration): List<CordaConsumerRecord<K, V>> {
        TODO("Not yet implemented")
    }

    override fun resetToLastCommittedPositions(offsetStrategy: CordaOffsetResetStrategy) {
        TODO("Not yet implemented")
    }

    override fun commitSyncOffsets(event: CordaConsumerRecord<K, V>, metaData: String?) {
        TODO("Not yet implemented")
    }

    override fun getPartitions(topic: String, timeout: Duration): List<CordaTopicPartition> {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun setDefaultRebalanceListener(defaultListener: CordaConsumerRebalanceListener) {
        TODO("Not yet implemented")
    }

    // Will be implemented (or changed) in the next PR
    fun getConsumerGroup(): String {
        return ""
    }
}
