package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.properties.InMemoryConfiguration
import java.util.concurrent.ConcurrentHashMap

class Topics(
    private val config: InMemoryConfiguration = InMemoryConfiguration()
) {
    private val topics: ConcurrentHashMap<String, Topic> = ConcurrentHashMap()

    internal fun getWriteLock(records: Collection<Record<*, *>>): PartitionsWriteLock {
        val partitions = records.map {
            val topic = requireNotNull(it.topic) { "Topic is not allowed to be null for message bus records" }
            getTopic(topic).getPartition(it)
        }
        return PartitionsWriteLock(partitions)
    }
    internal fun getWriteLock(records: Collection<Record<*, *>>, partitionId: Int): PartitionsWriteLock {
        val partitions = records.map {
            val topic = requireNotNull(it.topic) { "Topic is not allowed to be null for message bus records" }
            getTopic(topic).getPartition(partitionId)
        }
        return PartitionsWriteLock(partitions)
    }

    internal fun getTopic(topicName: String): Topic {
        return topics.computeIfAbsent(topicName) {
            Topic(topicName, config.topicConfiguration(topicName))
        }
    }

    fun createConsumption(consumer: Consumer): Consumption {
        return getTopic(consumer.topicName)
            .createConsumption(
                consumer,
                config.subscriptionConfiguration(consumer.groupName)
            )
    }

    fun getLatestOffsets(topicName: String): Map<Int, Long> {
        return getTopic(topicName).getLatestOffsets()
    }
}
