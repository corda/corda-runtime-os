package net.corda.messaging.emulation.topic.model

import net.corda.messaging.emulation.properties.InMemoryConfiguration
import java.util.concurrent.ConcurrentHashMap

class Topics(
    private val config: InMemoryConfiguration = InMemoryConfiguration()
) {
    private val topics: ConcurrentHashMap<String, Topic> = ConcurrentHashMap()

    fun getTopic(topicName: String): Topic {
        return topics.computeIfAbsent(topicName) {
            Topic(topicName, config.topicConfiguration(topicName))
        }
    }

    fun createConsumerThread(consumer: Consumer): ConsumerThread {
        val topic = getTopic(consumer.topicName)
        val configuration = config.subscriptionConfiguration(consumer.groupName)
        return ConsumerThread(consumer, topic, configuration)
    }
}
