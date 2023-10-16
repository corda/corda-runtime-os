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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.file.Paths

class CreateConnectTest {

    @Test
    fun `validate empty topic list`() {
        assertThat(getCommandWithGeneratedConfig().getTopics(emptyList())).isEmpty()
    }

    @Test
    fun `validate new topic with no config`() {
        assertThat(getCommandWithGeneratedConfig().getTopics(listOf(Create.PreviewTopicConfiguration("topic", 5, 3, emptyMap()))))
            .containsEntry("topic", NewTopic("topic", 5, 3).configs(emptyMap()))
    }

    @Test
    fun `validate new topic with config`() {
        assertThat(
            getCommandWithGeneratedConfig().getTopics(
                listOf(Create.PreviewTopicConfiguration("topic", 5, 3, mapOf("key" to "value")))
            )
        ).containsEntry("topic", NewTopic("topic", 5, 3).configs(mapOf("key" to "value")))
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

    @Test
    fun `validate acls created from config file with overrides`() {
        val cmd = getCommandWithConfigAndOverrideFiles()
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
                AclBinding(ResourcePattern(ResourceType.TOPIC, "avro.schema", PatternType.LITERAL),
                    AccessControlEntry("User:Mo", "*", AclOperation.WRITE, AclPermissionType.ALLOW)),
                AclBinding(ResourcePattern(ResourceType.TOPIC, "certificates.rpc.ops", PatternType.LITERAL),
                    AccessControlEntry("User:Dan", "*", AclOperation.READ, AclPermissionType.ALLOW)),
                AclBinding(ResourcePattern(ResourceType.TOPIC, "certificates.rpc.ops", PatternType.LITERAL),
                    AccessControlEntry("User:Dan", "*", AclOperation.DESCRIBE, AclPermissionType.ALLOW)),
                AclBinding(ResourcePattern(ResourceType.TOPIC, "certificates.rpc.ops", PatternType.LITERAL),
                    AccessControlEntry("User:George", "*", AclOperation.READ, AclPermissionType.ALLOW))

            )
    }

    @Test
    fun `validate topics created from config file`() {
        val cmd = getCommandWithConfigFile()
        val topics = cmd.getGeneratedTopicConfigs().topics
        assertThat(cmd.getTopics(topics))
            .containsEntry("avro.schema", NewTopic("avro.schema", 5, 3)
                .configs(mapOf("cleanup.policy" to "compact", "segment.ms" to "600000",
                    "delete.retention.ms" to "300000", "min.compaction.lag.ms" to "60000",
                    "max.compaction.lag.ms" to "604800000", "min.cleanable.dirty.ratio" to "0.5")))
            .containsEntry("certificates.rpc.ops", NewTopic("certificates.rpc.ops", 4, 2)
                .configs(emptyMap()))
    }

    @Test
    fun `validate topics created from config file with overrides`() {
        val cmd = getCommandWithConfigAndOverrideFiles()
        val topics = cmd.getGeneratedTopicConfigs().topics
        assertThat(cmd.getTopics(topics))
            .containsEntry("avro.schema", NewTopic("avro.schema", 8, 3)
                .configs(mapOf("cleanup.policy" to "compact", "segment.ms" to "600000",
                    "delete.retention.ms" to "300000", "min.compaction.lag.ms" to "60000",
                    "max.compaction.lag.ms" to "604800000", "min.cleanable.dirty.ratio" to "0.7")))
            .containsEntry("certificates.rpc.ops", NewTopic("certificates.rpc.ops", 4, 2)
                .configs(emptyMap()))
    }

    @Test
    fun `kafka server address is mandatory`() {
        val command = CreateConnect().apply {
            create = Create()
            create?.topic = TopicPlugin.Topic()
        }
        val baos = ByteArrayOutputStream()
        System.setOut(PrintStream(baos))
        command.run()
        System.setOut(PrintStream(FileOutputStream(FileDescriptor.out)))

        assertEquals("Required parameters missing: kafka bootstrap server [-b, --bootstrap-server]",
            baos.toString().trim())
    }

    private fun getCommandWithGeneratedConfig() = CreateConnect().apply {
        create = Create()
        create!!.topic = TopicPlugin.Topic()
        create!!.kafkaUsers = mapOf("crypto" to "Chris", "db" to "Dan", "flow" to "Fiona", "membership" to "Mo")
    }

    private fun getCommandWithConfigFile() = CreateConnect().apply {
        val configFile = this::class.java.classLoader.getResource("short_generated_topic_config.yaml")!!.toURI()
        configFilePath = Paths.get(configFile).toString()
        create = Create()
        create!!.topic = TopicPlugin.Topic()
    }

    private fun getCommandWithConfigAndOverrideFiles() = CreateConnect().apply {
        val configFile = this::class.java.classLoader.getResource("short_generated_topic_config.yaml")!!.toURI()
        configFilePath = Paths.get(configFile).toString()
        create = Create()
        create!!.topic = TopicPlugin.Topic()
        val overrideFile = this::class.java.classLoader.getResource("override_topic_config.yaml")!!.toURI()
        create!!.overrideFilePath = Paths.get(overrideFile).toString()
    }
}
