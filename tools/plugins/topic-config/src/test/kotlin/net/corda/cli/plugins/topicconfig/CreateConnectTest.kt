package net.corda.cli.plugins.topicconfig

import com.fasterxml.jackson.module.kotlin.readValue
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
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths

class CreateConnectTest {

    @Test
    fun `validate empty topic list`() {
        assertThat(getCommandWithGeneratedConfig().getTopics(emptyList())).isEmpty()
    }

    @Test
    fun `validate new topic with no config`() {
        assertThat(getCommandWithGeneratedConfig().getTopics(listOf(Create.PreviewTopicConfiguration("topic", emptyMap()))))
            .containsEntry("topic", NewTopic("topic", 1, 1).configs(emptyMap()))
    }

    @Test
    fun `validate new topic with config`() {
        assertThat(getCommandWithGeneratedConfig().getTopics(listOf(Create.PreviewTopicConfiguration("topic", mapOf("key" to "value")))))
            .containsEntry("topic", NewTopic("topic", 1, 1).configs(mapOf("key" to "value")))
    }

    @Test
    fun `validate partition numbers are set correctly`() {
        val command = CreateConnect().apply {
            create = Create()
            create?.topic = TopicPlugin.Topic()
            create?.partitionOverride = 3 // set a global override
            create?.tagsToPropertiesMap = mapOf("tag01" to "partitions:5") // set partition number for tagged topics
        }

        val topicDefinitionsFile = this::class.java.classLoader.getResource("config.yaml")?.toURI()
        val topicDefinitionsString = Files.readString(File(topicDefinitionsFile!!).toPath())
        val topicDefinitions: Create.TopicDefinitions = command.create!!.mapper.readValue(topicDefinitionsString)
        val previewConfig = command.create!!.getTopicConfigsForPreview(topicDefinitions.topics.values.toList())

        var topicsToBeCreated = command.getTopics(previewConfig.topics, Paths.get(topicDefinitionsFile).toString())
        assertThat(topicsToBeCreated).containsEntry(
            "config.management.request",
            NewTopic("config.management.request", 5, 1).configs(emptyMap()))
        assertThat(topicsToBeCreated).containsEntry(
            "config.management.request.resp",
            NewTopic("config.management.request.resp", 3, 1).configs(emptyMap()))
        assertThat(topicsToBeCreated).containsEntry(
            "config.topic",
            NewTopic("config.topic", 3, 1).configs(mapOf(
                "cleanup.policy" to "compact",
                "segment.ms" to "600000",
                "delete.retention.ms" to "300000",
                "min.compaction.lag.ms" to "60000",
                "max.compaction.lag.ms" to "604800000",
                "min.cleanable.dirty.ratio" to "0.5"
        )))

        // tag arguments do not exist, default will be set
        command.create?.partitionOverride = 1
        command.create?.tagsToPropertiesMap = mapOf("tag02" to "partitions:5")
        topicsToBeCreated = command.getTopics(previewConfig.topics, Paths.get(topicDefinitionsFile).toString())
        assertThat(topicsToBeCreated).containsEntry(
            "config.management.request",
            NewTopic("config.management.request", 1, 1).configs(emptyMap()))
        assertThat(topicsToBeCreated).containsEntry(
            "config.management.request.resp",
            NewTopic("config.management.request.resp", 1, 1).configs(emptyMap()))
        assertThat(topicsToBeCreated).containsEntry(
            "config.topic",
            NewTopic("config.topic", 1, 1).configs(mapOf(
                "cleanup.policy" to "compact",
                "segment.ms" to "600000",
                "delete.retention.ms" to "300000",
                "min.compaction.lag.ms" to "60000",
                "max.compaction.lag.ms" to "604800000",
                "min.cleanable.dirty.ratio" to "0.5"
        )))

        // tag arguments have invalid format or unsupported values
        command.create?.tagsToPropertiesMap = mapOf("tag01" to "party:5")
        assertThrows<IllegalArgumentException> {
            command.getTopics(previewConfig.topics, Paths.get(topicDefinitionsFile).toString())
        }

        command.create?.tagsToPropertiesMap = mapOf("tag01" to "partitions:abc")
        assertThrows<IllegalArgumentException> {
            command.getTopics(previewConfig.topics, Paths.get(topicDefinitionsFile).toString())
        }
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
}
