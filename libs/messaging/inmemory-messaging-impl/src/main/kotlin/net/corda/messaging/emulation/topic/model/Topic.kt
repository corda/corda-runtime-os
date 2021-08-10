package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.properties.TopicConfiguration
import java.util.concurrent.ConcurrentHashMap

/**
 * Object to store records and track consumer group offsets.
 * Topics have a maximum size. When max size is reached oldest records are deleted.
 * Topics have locks which must be obtained to read or write.
 * Consumers subscribing and producers writing to topics automatically create topics if they do not exist.
 */
class Topic(
    private val topicName: String,
    internal val topicConfiguration: TopicConfiguration,
) {
    private val partitions by lazy {
        (1..topicConfiguration.partitionCount).map {
            Partition(
                it,
                topicConfiguration.maxSize,
                topicConfiguration.pollSize,
                topicName
            )
        }
    }

    private val consumers = ConcurrentHashMap<String, ConsumerGroup>()

    /**
     * Subscribe the [consumer] to this [topicName]
     */
    fun subscribe(consumer: Consumer) {
        consumers.computeIfAbsent(consumer.groupName) {
            ConsumerGroup(consumer.groupName, topicName, partitions)
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
        val partitionNumber = Math.abs(record.key.hashCode() % partitions.size)
        val partition = partitions[partitionNumber]
        partition.addRecord(record)
        consumers.values.forEach {
            it.wakeUp()
        }
    }
}
