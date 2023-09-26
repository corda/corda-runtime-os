package net.corda.cli.plugins.topicconfig

import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class GenerateTest {

    @Test
    fun `validate topic configuration is generated correctly`() {
        val command = command()
        command.create!!.topic!!.bootstrapServer = "address" // not used, should be ignored
        command.create!!.topic!!.kafkaConfig = "/tmp/working_dir/config.properties"

        val expectedConfigYamlFile = javaClass.classLoader.getResource("topic_generated_config.yaml")?.file
        val expectedConfigString = Files.readString(File(expectedConfigYamlFile!!).toPath())
        val expectedConfig: Create.GeneratedTopicDefinitions = command.create!!.mapper.readValue(expectedConfigString)

        val actualConfig = command.create!!.getGeneratedTopicConfigs()

        assertEquals(expectedConfig, actualConfig)
    }

    private fun command() : Generate {
        val generate = Generate()
        generate.create = Create()
        generate.create!!.topic = TopicPlugin.Topic()
        generate.create!!.kafkaUsers = mapOf("crypto" to "Chris", "db" to "Dan", "flow" to "Fiona", "membership" to "Mo")
        return generate
    }
}