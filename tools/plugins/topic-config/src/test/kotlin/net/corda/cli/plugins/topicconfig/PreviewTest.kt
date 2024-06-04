package net.corda.cli.plugins.topicconfig

import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.sdk.bootstrap.topicconfig.TopicConfigCreator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class PreviewTest {

    @Test
    fun `validate topic configuration is generated correctly`() {
        val command = command()

        val expectedConfigYamlFile = this::class.java.classLoader.getResource("preview_config.yaml")?.toURI()
        val expectedConfigString = Files.readString(File(expectedConfigYamlFile!!).toPath())
        val expectedConfig: TopicConfigCreator.PreviewTopicConfigurations = command
            .create!!
            .topicConfigCreator
            .mapper
            .readValue(expectedConfigString)


        val topicDefinitionsFile = this::class.java.classLoader.getResource("config.yaml")?.toURI()
        val topicDefinitionsString = Files.readString(File(topicDefinitionsFile!!).toPath())
        val topicDefinitions: TopicConfigCreator.TopicDefinitions = command
            .create!!
            .topicConfigCreator
            .mapper
            .readValue(topicDefinitionsString)
        val actualConfig = command.create!!.getTopicConfigsForPreview(topicDefinitions.topics.values.toList())

        assertEquals(expectedConfig, actualConfig)
    }

    private fun command() : Preview {
        val preview = Preview()
        preview.create = Create()
        preview.create!!.topic = TopicPlugin()
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
        preview.create!!.topic!!.namePrefix = "prefix."
        return preview
    }
}
