package net.corda.introspiciere.core

import net.corda.introspiciere.domain.IntrospiciereException
import net.corda.introspiciere.domain.TopicDefinition
import net.corda.introspiciere.domain.TopicDescription
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException
import java.util.*


interface TopicGateway {
    fun create(topicDefinition: TopicDefinition): Boolean
    fun removeByName(name: String): Boolean
    fun findAll(): Set<String>
    fun findByName(name: String): TopicDescription?

    class TopicAlreadyExist(topic: String) : IntrospiciereException("Topic $topic already exists")
    class TopicDoesNotExist(topic: String) : IntrospiciereException("Topic $topic does not exists")
}

class TopicGatewayImpl(private val kafkaConfig: KafkaConfig) : TopicGateway {

    override fun create(topicDefinition: TopicDefinition): Boolean {
        val topic = NewTopic(
            topicDefinition.name,
            Optional.ofNullable(topicDefinition.partitions),
            Optional.ofNullable(topicDefinition.replicationFactor)
        )

        if (topicDefinition.config.isNotEmpty()) {
            topic.configs(topicDefinition.config)
        }

        adminClient().use {
            val result = it.createTopics(listOf(topic))
            result.all().get()
        }

        return true
    }

    override fun removeByName(name: String): Boolean {
        adminClient().use {
            return try {
                val future = it.deleteTopics(listOf(name))
                future.all().get()
                true
            } catch (ex: UnknownTopicOrPartitionException) {
                false
            }
        }
    }

    override fun findAll(): Set<String> {
        return adminClient().use { it.listTopics().names().get() }
    }

    override fun findByName(name: String): TopicDescription? {
        val topic = adminClient().use {
            it.describeTopics(listOf(name)).all().get().values.firstOrNull()
        } ?: return null

        return TopicDescription(
            topic.topicId().toString(),
            topic.name(),
            topic.partitions().size,
            topic.partitions().first().replicas().size.toShort()
        )
    }

    private fun adminClient(): Admin = Admin.create(mapOf(
        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaConfig.brokers
    ))
}