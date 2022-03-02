package net.corda.introspiciere.junit

import net.corda.introspiciere.domain.TopicDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class TopicManagementTest {

    @RegisterExtension
    private val introspiciere = FakeIntrospiciereServer()

    @Test
    fun `create a topic`() {
        val name = "topic1"
        introspiciere.client.createTopic(name)

        val topics = introspiciere.appContext.topicGateway.findAll()
        assertEquals(1, topics.size, "Only one topic")
        assertEquals(name, topics.single(), "Topic name")
    }

    @Test
    fun `list all my topics`() {
        val names = listOf("topic1", "topic2")
        introspiciere.appContext.topicGateway.create(TopicDefinition(names[0]))
        introspiciere.appContext.topicGateway.create(TopicDefinition(names[1]))

        assertEquals(names.toSet(), introspiciere.client.listTopics())
    }

    @Test
    fun `describe topic`() {
        val definition = TopicDefinition("topic1", 3, 2)
        introspiciere.appContext.topicGateway.create(definition)

        val description = introspiciere.client.describeTopic(definition.name)
        assertEquals(definition.name, description.name, "Topic name")
        assertEquals(definition.partitions, description.partitions, "Topic partitions")
        assertEquals(definition.replicationFactor, description.replicas, "Topic replica")
    }

    @Test
    @Disabled
    fun `delete a topic`() {
        val definition = TopicDefinition("topic1")
        introspiciere.appContext.topicGateway.create(definition)

        introspiciere.client.deleteTopic(definition.name)

        println(introspiciere.appContext.topicGateway.findAll())
        assertFalse(
            definition.name in introspiciere.appContext.topicGateway.findAll(),
            "Topic should not exist"
        )
    }
}