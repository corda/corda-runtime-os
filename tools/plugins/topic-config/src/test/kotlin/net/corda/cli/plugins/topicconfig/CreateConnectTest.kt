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
    fun `validate new topic with no config`() {
        assertThat(command().getTopics(listOf(Create.TopicConfig("topic", emptyList(), emptyList(), emptyMap()))))
            .containsEntry("topic", NewTopic("topic", 1, 1).configs(emptyMap()))
    }

    @Test
    fun `validate new topic with config`() {
        assertThat(command().getTopics(listOf(Create.TopicConfig("topic", emptyList(), emptyList(), mapOf("key" to "value")))))
            .containsEntry("topic", NewTopic("topic", 1, 1).configs(mapOf("key" to "value")))
    }

    @Test
    fun `validate acls with consumer and producer`() {
        assertThat(command().getAclBindings(listOf(Create.TopicConfig("topic", listOf("db"), listOf("flow")))))
            .containsExactly(
                AclBinding(ResourcePattern(ResourceType.TOPIC, "topic", PatternType.LITERAL),
                    AccessControlEntry("User:Dan", "*", AclOperation.READ, AclPermissionType.ALLOW)),
                AclBinding(ResourcePattern(ResourceType.TOPIC, "topic", PatternType.LITERAL),
                    AccessControlEntry("User:Fiona", "*", AclOperation.WRITE, AclPermissionType.ALLOW))
            )
    }

    private fun command() : CreateConnect {
        val createConnect = CreateConnect()
        createConnect.create = Create()
        createConnect.create!!.topic = TopicPlugin.Topic()
        createConnect.create!!.kafkaUsers = mapOf("db" to "Dan", "flow" to "Fiona")
        return createConnect
    }
}
