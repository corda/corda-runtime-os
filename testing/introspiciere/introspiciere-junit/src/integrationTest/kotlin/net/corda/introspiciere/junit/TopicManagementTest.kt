package net.corda.introspiciere.junit

import net.corda.introspiciere.domain.TopicDefinition
import org.junit.jupiter.api.Assertions
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
        Assertions.assertEquals(1, topics.size, "Only one topic")
        Assertions.assertEquals(name, topics.single(), "Topic name")
    }

    @Test
    fun `list all my topics`() {
        val names = listOf("topic1", "topic2")
        introspiciere.appContext.topicGateway.create(TopicDefinition(names[0]))
        introspiciere.appContext.topicGateway.create(TopicDefinition(names[1]))

        Assertions.assertEquals(names.toSet(), introspiciere.client.listTopics())
    }

    @Test
    fun `describe topic`() {
        val definition = TopicDefinition("topic1", 3, 2)
        introspiciere.appContext.topicGateway.create(definition)

        val description = introspiciere.client.describeTopic(definition.name)
        Assertions.assertEquals(definition.name, description.name, "Topic name")
        Assertions.assertEquals(definition.partitions, description.partitions, "Topic partitions")
        Assertions.assertEquals(definition.replicationFactor, description.replicas, "Topic replica")
    }

    @Test
    fun `delete a topic`() {
        val definition = TopicDefinition("topic1")
        introspiciere.appContext.topicGateway.create(definition)

        introspiciere.client.deleteTopic(definition.name)

        Assertions.assertFalse(
            definition.name in introspiciere.appContext.topicGateway.findAll(),
            "Topic should not exist"
        )
    }
}