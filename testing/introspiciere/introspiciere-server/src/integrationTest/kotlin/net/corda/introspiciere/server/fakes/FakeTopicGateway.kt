package net.corda.introspiciere.server.fakes

import net.corda.introspiciere.core.TopicGateway
import net.corda.introspiciere.domain.TopicDefinition
import net.corda.introspiciere.domain.TopicDescription
import java.util.*

class FakeTopicGateway : TopicGateway {

    private val topics = mutableMapOf<String, TopicDescription>()

    override fun create(topicDefinition: TopicDefinition): Boolean {
        topicMustNotExist(topicDefinition.name)
        topics[topicDefinition.name] = TopicDescription(
            UUID.randomUUID().toString(),
            topicDefinition.name,
            topicDefinition.partitions ?: 1,
            topicDefinition.replicationFactor ?: 1
        )
        return true
    }

    override fun removeByName(name: String): Boolean {
        val value = topics.remove(name)
        return value != null // null if the value is not present
    }

    override fun findAll(): Set<String> {
        return topics.keys
    }

    override fun findByName(name: String): TopicDescription? {
        return topics[name]
    }

    private fun topicMustNotExist(name: String) {
        if (name in topics) throw TopicGateway.TopicAlreadyExist(name)
    }

    private fun topicMustExist(name: String) {
        if (name !in topics) throw TopicGateway.TopicDoesNotExist(name)
    }
}