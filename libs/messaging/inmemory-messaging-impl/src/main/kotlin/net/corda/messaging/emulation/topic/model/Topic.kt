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
     * Subscribe the [consumerDefinitions] to this [topicName]
     */
    fun createConsumption(
        consumerDefinitions: ConsumerDefinitions,
        subscriptionConfiguration: SubscriptionConfiguration
    ): Consumption {
        return consumerGroups.computeIfAbsent(consumerDefinitions.groupName) {
            ConsumerGroup(consumerDefinitions.topicName, partitions, subscriptionConfiguration)
        }.createConsumption(consumerDefinitions)
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

    /**
     * Unsubscribe the [consumerDefinitions] to this [topicName]
     */
    fun unsubscribe(consumerDefinitions: ConsumerDefinitions) {
        consumerGroups[consumerDefinitions.groupName]?.stopConsuming(consumerDefinitions)
    }

    /**
     * Add this [record] to this [topicName].
     * If [record] max size is reached, delete the oldest record
     */
    fun addRecord(record: Record<*, *>) {
        getPartition(record).addRecord(record)
        consumerGroups.values.forEach {
            it.wakeUp()
        }
    }

    /**
     * Add this [record] to this [topicName] with specific partition number.
     * If [record] max size is reached, delete the oldest record
     */
    fun addRecordToPartition(record: Record<*, *>, partitionId: Int) {
        getPartition(partitionId)
            .addRecord(record)
        consumerGroups.values.forEach {
            it.wakeUp()
        }
    }

    fun getLatestOffsets(): Map<Int, Long> {
        return partitions.associate {
            it.partitionId to it.latestOffset() - 1
        }
    }
}
