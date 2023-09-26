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
        assertThat(getCommandWithGeneratedConfig().getTopics(emptyList())).isEmpty()
    }

    @Test
    fun `validate new topic with no config`() {
        assertThat(getCommandWithGeneratedConfig().getTopics(listOf(Create.GeneratedTopicConfig("topic", emptyMap()))))
            .containsEntry("topic", NewTopic("topic", 1, 1).configs(emptyMap()))
    }

    @Test
    fun `validate new topic with config`() {
        assertThat(getCommandWithGeneratedConfig().getTopics(listOf(Create.GeneratedTopicConfig("topic", mapOf("key" to "value")))))
            .containsEntry("topic", NewTopic("topic", 1, 1).configs(mapOf("key" to "value")))
    }

    @Test
    fun `validate acls created from config file`() {
        val cmd = getCommandWithConfigFile()
        val acls = cmd.getGeneratedTopicConfigs().acls
        assertThat(cmd.getAclBindings(acls))
            .containsExactly(
                AclBinding(ResourcePattern(ResourceType.TOPIC, "avro.schema", PatternType.LITERAL),
                    AccessControlEntry("User:Chris", "*", AclOperation.READ, AclPermissionType.ALLOW)),
                AclBinding(ResourcePattern(ResourceType.TOPIC, "avro.schema", PatternType.LITERAL),
                    AccessControlEntry("User:Chris", "*", AclOperation.WRITE, AclPermissionType.ALLOW)),
                AclBinding(ResourcePattern(ResourceType.TOPIC, "avro.schema", PatternType.LITERAL),
                    AccessControlEntry("User:Chris", "*", AclOperation.DESCRIBE, AclPermissionType.ALLOW)),
                AclBinding(ResourcePattern(ResourceType.TOPIC, "avro.schema", PatternType.LITERAL),
                    AccessControlEntry("User:Mo", "*", AclOperation.READ, AclPermissionType.ALLOW)),
                AclBinding(ResourcePattern(ResourceType.TOPIC, "avro.schema", PatternType.LITERAL),
                    AccessControlEntry("User:Mo", "*", AclOperation.DESCRIBE, AclPermissionType.ALLOW)),
                AclBinding(ResourcePattern(ResourceType.TOPIC, "certificates.rpc.ops", PatternType.LITERAL),
                    AccessControlEntry("User:Dan", "*", AclOperation.READ, AclPermissionType.ALLOW)),
                AclBinding(ResourcePattern(ResourceType.TOPIC, "certificates.rpc.ops", PatternType.LITERAL),
                    AccessControlEntry("User:Dan", "*", AclOperation.DESCRIBE, AclPermissionType.ALLOW))

            )
    }

    private fun getCommandWithGeneratedConfig() = CreateConnect().apply {
        create = Create()
        create!!.topic = TopicPlugin.Topic()
        create!!.kafkaUsers = mapOf("crypto" to "Chris", "db" to "Dan", "flow" to "Fiona", "membership" to "Mo")
    }

    private fun getCommandWithConfigFile() = CreateConnect().apply {
        configFilePath = javaClass.classLoader.getResource("short_generated_topic_config.yaml")?.path
        create = Create()
        create!!.topic = TopicPlugin.Topic()
    }
}
