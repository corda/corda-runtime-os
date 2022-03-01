package net.corda.introspiciere.junit

import net.corda.introspiciere.core.TopicGateway
import net.corda.introspiciere.domain.TopicDefinition
import net.corda.introspiciere.domain.TopicDescription
import java.util.*

class FakeTopicGateway : TopicGateway {

    private val topics = mutableMapOf<String, TopicDescription>()

    override fun create(topicDefinition: TopicDefinition) {
        topicMustNotExist(topicDefinition.name)
        topics[topicDefinition.name] = TopicDescription(
            UUID.randomUUID().toString(),
            topicDefinition.name,
            topicDefinition.partitions ?: 1,
            topicDefinition.replicationFactor ?: 1
        )
    }

    override fun removeByName(name: String) {
        topicMustExist(name)
        topics.remove(name)
    }

    override fun findAll(): Set<String> {
        return topics.keys
    }

    override fun findByName(name: String): TopicDescription {
        topicMustExist(name)
        return topics[name]!!
    }

    private fun topicMustNotExist(name: String) {
        if (name in topics) throw TopicGateway.TopicAlreadyExist(name)
    }

    private fun topicMustExist(name: String) {
        if (name !in topics) throw TopicGateway.TopicDoesNotExist(name)
    }
}