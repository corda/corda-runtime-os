package net.corda.introspiciere.core

import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import java.util.*

/**
 * Use case to create a topic in Kafka.
 */
class CreateTopicUseCase(private val kafkaConfig: KafkaConfig) : UseCase<CreateTopicUseCase.Input> {

    data class Input(
        val name: String,
        val partitions: Int? = null,
        val replicationFactor: Short? = null,
        val config: Map<String, String> = emptyMap(),
    )

    override fun execute(input: Input) {
        val topic = NewTopic(
            input.name,
            Optional.ofNullable(input.partitions),
            Optional.ofNullable(input.replicationFactor)
        )

        if (input.config.isNotEmpty()) {
            topic.configs(input.config)
        }

        val props = mapOf(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaConfig.brokers
        )

        Admin.create(props).use {
            it.createTopics(listOf(topic))
        }
    }
}