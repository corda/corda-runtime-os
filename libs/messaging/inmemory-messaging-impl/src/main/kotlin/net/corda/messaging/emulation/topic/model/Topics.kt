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

    fun createConsumption(consumerDefinitions: ConsumerDefinitions): Consumption {
        val topic = getTopic(consumerDefinitions.topicName)
        val configuration = config.subscriptionConfiguration(consumerDefinitions.groupName)
        return ConsumptionThread(consumerDefinitions, topic, configuration)
    }
}
