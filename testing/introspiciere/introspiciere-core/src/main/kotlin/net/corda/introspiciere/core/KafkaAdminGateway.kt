package net.corda.introspiciere.core

import net.corda.introspiciere.domain.TopicDefinition
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import java.util.*
import kotlin.concurrent.getOrSet

interface KafkaAdminGateway {
    fun createTopic(definition: TopicDefinition)
}

class KafkaAdminGatewayImpl(private val kafkaConfig: KafkaConfig) : KafkaAdminGateway {

    override fun createTopic(definition: TopicDefinition) {
        val topic = NewTopic(
            definition.name,
            Optional.ofNullable(definition.partitions),
            Optional.ofNullable(definition.replicationFactor)
        )

        if (definition.config.isNotEmpty()) {
            topic.configs(definition.config)
        }

        val results = adminClient.createTopics(listOf(topic))
        results.all().get()
    }

    private val adminClient: Admin
        get() = ThreadLocal<Admin>().getOrSet {
            mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaConfig.brokers).let(Admin::create)
        }
}

