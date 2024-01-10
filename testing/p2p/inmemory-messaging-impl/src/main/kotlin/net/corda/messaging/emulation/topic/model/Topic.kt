package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.properties.SubscriptionConfiguration
import net.corda.messaging.emulation.properties.TopicConfiguration
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Object to store records and track consumer group offsets.
 * Topics have a maximum size. When the max size is reached the oldest records are deleted.
 * Topics have locks which must be obtained to read or write.
 * Consumers subscribing and producers writing to topics automatically create topics if they do not exist.
 */
internal class Topic(
    private val topicName: String,
    private val topicConfiguration: TopicConfiguration,
) {
    private val partitions = let {
        (1..topicConfiguration.partitionCount).map {
            Partition(
                it,
                topicConfiguration.maxPartitionSize,
                topicName
            )
        }
    }

    private val consumerGroups = ConcurrentHashMap<String, ConsumerGroup>()

    /**
     * Subscribe the [consumer] to this [topicName]
     */
    fun createConsumption(
        consumer: Consumer,
        subscriptionConfiguration: SubscriptionConfiguration
    ): Consumption {
        return consumerGroups.computeIfAbsent(consumer.groupName) {
            ConsumerGroup(
                partitions,
                subscriptionConfiguration,
                consumer,
            )
        }.createConsumption(consumer)
    }

    fun getPartition(record: Record<*, *>): Partition {
        val partitionNumber = abs(record.key.hashCode() % partitions.size)
        return partitions[partitionNumber]
    }

    fun getPartition(partitionId: Int): Partition {
        return partitions.firstOrNull {
            it.partitionId == partitionId
        }
            ?: throw IllegalStateException("Could not find partition id $partitionId, only know of ${partitions.map { it.partitionId }}!")
    }

    fun assignPartition(consumer: Consumer, partitionsIds: Collection<Int>) {
        val partitions = partitionsIds.map {
            getPartition(it)
        }
        val group = consumerGroups[consumer.groupName] ?: throw IllegalStateException("Group ${consumer.groupName} had not subscribed")
        group.assignPartition(consumer, partitions)
    }

    fun unAssignPartition(consumer: Consumer, partitionsIds: Collection<Int>) {
        val partitions = partitionsIds.map {
            getPartition(it)
        }
        val group = consumerGroups[consumer.groupName] ?: throw IllegalStateException("Group ${consumer.groupName} had not subscribed")
        group.unAssignPartition(consumer, partitions)
    }

    /**
     * Unsubscribe the [consumer] to this [topicName]
     */
    fun unsubscribe(consumer: Consumer) {
        consumerGroups[consumer.groupName]?.stopConsuming(consumer)
    }

    /**
     * Add this [record] to this [topicName].
     * If [record] max size is reached, delete the oldest record
     */
    fun addRecord(record: Record<*, *>) {
        getPartition(record).addRecord(record)
    }

    /**
     * Add this [record] to this [topicName] with specific partition number.
     * If [record] max size is reached, delete the oldest record
     */
    fun addRecordToPartition(record: Record<*, *>, partitionId: Int) {
        getPartition(partitionId)
            .addRecord(record)
    }

    fun getLatestOffsets(): Map<Int, Long> {
        return partitions.associate {
            it.partitionId to it.latestOffset() - 1
        }
    }

    fun wakeUpConsumers() {
        consumerGroups.values.forEach {
            it.wakeUp()
        }
    }
}
