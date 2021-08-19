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
            val topic = getTopic(it.topic)
            topic.getPartition(it)
        }
        return PartitionsWriteLock(partitions)
    }
    internal fun getWriteLock(records: Collection<Record<*, *>>, partitionId: Int): PartitionsWriteLock {
        val partitions = records.map {
            val topic = getTopic(it.topic)
            topic.getPartition(partitionId)
        }
        return PartitionsWriteLock(partitions)
    }

    internal fun getTopic(topicName: String): Topic {
        return topics.computeIfAbsent(topicName) {
            Topic(topicName, config.topicConfiguration(topicName))
        }
    }

    fun createConsumption(consumerDefinitions: ConsumerDefinitions): Consumption {
        return getTopic(consumerDefinitions.topicName)
            .createConsumption(
                consumerDefinitions,
                config.subscriptionConfiguration(consumerDefinitions.groupName)
            )
    }

    fun getLatestOffsets(topicName: String): Map<Int, Long> {
        return getTopic(topicName).getLatestOffsets()
    }
}
