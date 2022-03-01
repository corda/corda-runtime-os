package net.corda.introspiciere.core

import net.corda.introspiciere.domain.TopicDefinition
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.common.config.TopicConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ThreadLocalRandom
import kotlin.streams.asSequence

internal class KafkaAdminGatewayTest {
    @Test
    fun `i can create a topic`() {
        val gateway: KafkaAdminGateway = KafkaAdminGatewayImpl(object : KafkaConfig {
            override val brokers: String = "20.62.51.171:9094"
        })

        val definition = TopicDefinition(
            name = "topic".random8,
            partitions = 3,
            replicationFactor = 2,
            config = mapOf(
                TopicConfig.CLEANUP_POLICY_CONFIG to "compact",
                TopicConfig.SEGMENT_MS_CONFIG to 300_000.toString(),
            )
        )
        gateway.createTopic(definition)

        val future = ThreadLocal<Admin>().get().describeTopics(listOf(definition.name))

        val description = future.all().get()[definition.name]
        assertNotNull(description, "Topic exists")
        assertEquals(definition.name, description!!.name())
        assertEquals(definition.partitions, description.partitions().size)
        assertEquals(definition.replicationFactor, description.partitions().first().replicas().size)
    }
}

private val charPool: List<Char> = ('a'..'z') + ('0'..'9')

internal val String.random8: String
    get() = "$this-" + ThreadLocalRandom.current().ints(8L, 0, charPool.size)
        .asSequence().map(charPool::get).joinToString("")



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
            name = "topic".random8,
            partitions = 3,
            replicationFactor = 2,
            config = mapOf(
                TopicConfig.CLEANUP_POLICY_CONFIG to "compact",
                TopicConfig.SEGMENT_MS_CONFIG to 300_000.toString(),
            )
        )

        assertFalse(definition.name in topicGateway.findAll(), "Topic should NOT exist before creating it")
        topicGateway.create(definition)
        assertTrue(definition.name in topicGateway.findAll(), "Topic should exist after creating it")

        val description = topicGateway.findByName(definition.name)
        assertNotNull(description.id, "Topic id")
        assertEquals(definition.name, description.name, "Topic name")
        assertEquals(definition.partitions, description.partitions, "Topic partitions")
        assertEquals(definition.replicationFactor, description.replicas, "Topic replicas")

        topicGateway.removeByName(definition.name)
        assertFalse(definition.name in topicGateway.findAll(), "Topic should NOT exist after deletion")
    }
}