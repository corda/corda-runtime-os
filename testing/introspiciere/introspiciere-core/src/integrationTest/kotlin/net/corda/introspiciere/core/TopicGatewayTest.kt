package net.corda.introspiciere.core

import net.corda.introspiciere.domain.TopicDefinition
import org.apache.kafka.common.config.TopicConfig
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

internal class TopicGatewayTest {

    private lateinit var topicGateway: TopicGateway

    @BeforeEach
    fun beforeEach() {
        topicGateway = TopicGatewayImpl(object : KafkaConfig {
            override val brokers: String = "20.62.51.171:9094"
        })
    }

    @Test
    fun `I can execute lifecycle of a topic`() {
        val definition = TopicDefinition(
            name = "topic-" + Instant.now().toEpochMilli(),
            partitions = 3,
            replicationFactor = 2,
            config = mapOf(
                TopicConfig.CLEANUP_POLICY_CONFIG to "compact",
                TopicConfig.SEGMENT_MS_CONFIG to 300_000.toString(),
            )
        )

        Assertions.assertFalse(definition.name in topicGateway.findAll(), "Topic should NOT exist before creating it")
        topicGateway.create(definition)
        Assertions.assertTrue(definition.name in topicGateway.findAll(), "Topic should exist after creating it")

        val description = topicGateway.findByName(definition.name)
        Assertions.assertNotNull(description.id, "Topic id")
        Assertions.assertEquals(definition.name, description.name, "Topic name")
        Assertions.assertEquals(definition.partitions, description.partitions, "Topic partitions")
        Assertions.assertEquals(definition.replicationFactor, description.replicas, "Topic replicas")

        topicGateway.removeByName(definition.name)
        Assertions.assertFalse(definition.name in topicGateway.findAll(), "Topic should NOT exist after deletion")
    }
}

