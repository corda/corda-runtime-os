package net.corda.cli.plugins.topicconfig

import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class PreviewTest {

    @Test
    fun `validate topic configuration is generated correctly`() {
        val command = command()
        command.create!!.topic!!.bootstrapServer = "address" // not used, should be ignored
        command.create!!.topic!!.kafkaConfig = "/tmp/working_dir/config.properties"

        val expectedConfigYamlFile = javaClass.classLoader.getResource("preview_config.yaml")?.file
        val expectedConfigString = Files.readString(File(expectedConfigYamlFile!!).toPath())
        val expectedConfig: Create.PreviewTopicConfigurations = command.create!!.mapper.readValue(expectedConfigString)


        val topicDefinitionsFile = javaClass.classLoader.getResource("config.yaml")?.file
        val topicDefinitionsString = Files.readString(File(topicDefinitionsFile!!).toPath())
        val topicDefinitions: Create.TopicDefinitions = command.create!!.mapper.readValue(topicDefinitionsString)
        val actualConfig = command.create!!.getTopicConfigsForPreview(topicDefinitions.topics.values.toList())

        assertEquals(expectedConfig, actualConfig)
    }

    private fun command() : Preview {
        val preview = Preview()
        preview.create = Create()
        preview.create!!.topic = TopicPlugin.Topic()
        preview.create!!.kafkaUsers = mapOf(
            "crypto" to "A",
            "db" to "B",
            "flow" to "C",
            "flowMapper" to "D",
            "verification" to "E",
            "membership" to "F",
            "p2pGateway" to "G",
            "p2pLinkManager" to "H",
            "persistence" to "I",
            "rest" to "J",
            "uniqueness" to "K")
        return preview
    }
}