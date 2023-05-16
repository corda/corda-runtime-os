package net.corda.cli.plugins.topicconfig

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.acl.AccessControlEntry
import org.apache.kafka.common.acl.AclBinding
import org.apache.kafka.common.acl.AclOperation
import org.apache.kafka.common.acl.AclPermissionType
import org.apache.kafka.common.resource.PatternType
import org.apache.kafka.common.resource.ResourcePattern
import org.apache.kafka.common.resource.ResourceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CreateConnectTest {

    @Test
    fun `validate empty topic list`() {
        assertThat(command().getTopics(emptyList())).isEmpty()
    }

    @Test
    fun `validate new topic with no consumers is ignored`() {
        assertThat(command().getTopics(listOf(Create.TopicConfig("topic", emptyList(), emptyList(), emptyMap()))))
            .isEmpty()
    }

    @Test
    fun `validate new topic with no config`() {
        assertThat(command().getTopics(listOf(Create.TopicConfig("topic", listOf("db"), emptyList(), emptyMap()))))
            .containsEntry("topic", NewTopic("topic", 1, 1).configs(emptyMap()))
    }

    @Test
    fun `validate new topic with config`() {
        assertThat(command().getTopics(listOf(Create.TopicConfig("topic", listOf("db"), emptyList(), mapOf("key" to "value")))))
            .containsEntry("topic", NewTopic("topic", 1, 1).configs(mapOf("key" to "value")))
    }

    @Test
    fun `validate new topic with two consumers has two partitions`() {
        assertThat(command().getTopics(listOf(Create.TopicConfig("topic", listOf("db", "flow"), emptyList(), mapOf("key" to "value")))))
            .containsEntry("topic", NewTopic("topic", 2, 1).configs(mapOf("key" to "value")))
    }

    @Test
    fun `validate new DLQ topic with multiple consumers has only one partition`() {
        assertThat(command().getTopics(listOf(Create.TopicConfig("topic.dlq", listOf("db", "flow"), emptyList(), mapOf("key" to "value")))))
            .containsEntry("topic.dlq", NewTopic("topic.dlq", 1, 1).configs(mapOf("key" to "value")))
    }

    @Test
    fun `validate new compacted topic with multiple consumers has only one partition`() {
        assertThat(command().getTopics(listOf(Create.TopicConfig("topic", listOf("db", "flow"), emptyList(), mapOf("cleanup.policy" to "compact")))))
            .containsEntry("topic", NewTopic("topic", 1, 1).configs(mapOf("cleanup.policy" to "compact")))
    }

    @Test
    fun `validate new state topic uses consumer count from event topic`() {
        assertThat(command().getTopics(listOf(Create.TopicConfig("topic.state", listOf("db"), emptyList(), mapOf("key" to "value")),
            Create.TopicConfig("topic", listOf("db", "flow"), emptyList(), mapOf("key" to "value")))))
            .containsEntry("topic.state", NewTopic("topic.state", 2, 1).configs(mapOf("key" to "value")))
    }

    @Test
    fun `validate acls with consumer and producer`() {
        assertThat(command().getAclBindings(listOf(Create.TopicConfig("topic", listOf("db"), listOf("flow")))))
            .containsExactly(
                AclBinding(ResourcePattern(ResourceType.TOPIC, "topic", PatternType.LITERAL),
                    AccessControlEntry("User:Dan", "*", AclOperation.READ, AclPermissionType.ALLOW)),
                AclBinding(ResourcePattern(ResourceType.TOPIC, "topic", PatternType.LITERAL),
                    AccessControlEntry("User:Dan", "*", AclOperation.DESCRIBE, AclPermissionType.ALLOW)),
                AclBinding(ResourcePattern(ResourceType.TOPIC, "topic", PatternType.LITERAL),
                    AccessControlEntry("User:Fiona", "*", AclOperation.WRITE, AclPermissionType.ALLOW)),
                AclBinding(ResourcePattern(ResourceType.TOPIC, "topic", PatternType.LITERAL),
                    AccessControlEntry("User:Fiona", "*", AclOperation.DESCRIBE, AclPermissionType.ALLOW))
            )
    }

    private fun command() : CreateConnect {
        val createConnect = CreateConnect()
        createConnect.create = Create()
        createConnect.create!!.topic = TopicPlugin.Topic()
        createConnect.create!!.kafkaUsers = mapOf("db" to "Dan", "flow" to "Fiona")
        createConnect.create!!.workerCounts = mapOf("db" to 1, "flow" to 1)
        return createConnect
    }
}
