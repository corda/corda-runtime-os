package net.corda.introspiciere.core

import net.corda.introspiciere.domain.TopicDefinition
import org.apache.kafka.clients.admin.NewTopic

class TopicCreatorGateway(private val kafkaAdminFactory: KafkaAdminFactory) {
    fun create(topicDefinition: TopicDefinition) {
        kafkaAdminFactory.create().use {
            val newTopic = NewTopic(topicDefinition.name, topicDefinition.partitions, topicDefinition.replicationFactor)
            it.createTopics(listOf(newTopic)).all().get()
        }
    }
}