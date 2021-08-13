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
class Topic(
    private val topicName: String,
    private val topicConfiguration: TopicConfiguration,
) {
    private val partitions by lazy {
        (1..topicConfiguration.partitionCount).map {
            Partition(
                it,
                topicConfiguration.maxSize,
                topicName
            )
        }
    }

    private val consumers = ConcurrentHashMap<String, ConsumerGroup>()

    /**
     * Subscribe the [consumer] to this [topicName]
     */
    fun subscribe(consumer: Consumer, subscriptionConfig: SubscriptionConfiguration) {
        consumers.computeIfAbsent(consumer.groupName) {
            ConsumerGroup(consumer.topicName, partitions, subscriptionConfig)
        }.subscribe(consumer)
    }

    /**
     * Unsubscribe the [consumer] to this [topicName]
     */
    fun unsubscribe(consumer: Consumer) {
        consumers[consumer.groupName]?.unsubscribe(consumer)
    }

    /**
     * Add this [record] to this [topicName].
     * If [record] max size is reached, delete the oldest record
     */
    fun addRecord(record: Record<*, *>) {
        val partitionNumber = abs(record.key.hashCode() % partitions.size)
        val partition = partitions[partitionNumber]
        partition.addRecord(record)
        consumers.values.forEach {
            it.wakeUp()
        }
    }

    /**
     * Add this [record] to this [topicName] with specific partition number.
     * If [record] max size is reached, delete the oldest record
     */
    fun addRecordToPartition(record: Record<*, *>, partitionId: Int) {
        val partition = partitions.firstOrNull {
            it.partitionId == partitionId
        }
            ?: throw IllegalStateException("Could not find partition id $partitionId, only know of ${partitions.map { it.partitionId }}!")
        partition.addRecord(record)
        consumers.values.forEach {
            it.wakeUp()
        }
    }

    fun handleAllRecords(handler: (Sequence<RecordMetadata>) -> Unit) {
        partitions.forEach {
            it.handleAllRecords(handler)
        }
    }
}
