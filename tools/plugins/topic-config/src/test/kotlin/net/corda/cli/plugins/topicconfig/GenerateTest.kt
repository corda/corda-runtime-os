package net.corda.cli.plugins.topicconfig

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.*
import java.nio.file.Files

class GenerateTest {

    @Test
    fun `validate topic configuration is generated correctly`() {
        val command = command()
        command.create!!.topic!!.bootstrapServer = "address" // not used, should be ignored
        command.create!!.topic!!.kafkaConfig = "/tmp/working_dir/config.properties"

        // Capture command output
        val baos = ByteArrayOutputStream()
        System.setOut(PrintStream(baos))
        command.run()
        // Restore STDOUT
        System.setOut(PrintStream(FileOutputStream(FileDescriptor.out)))
        val expectedConfigYaml = javaClass.classLoader.getResource("topic_generated_config.yaml")?.file
        val expectedLines = Files.readAllLines(File(expectedConfigYaml!!).toPath()).filter { it.isNotBlank() }
        val actualLines = baos.toString().split(System.lineSeparator()).filter { it.isNotBlank() }
        assertEquals(expectedLines.size, actualLines.size)
        expectedLines.forEachIndexed { index, line ->
            assertEquals(line.trim(), actualLines[index].trim())
        }
    }

    private fun command() : Generate {
        val generate = Generate()
        generate.create = Create()
        generate.create!!.topic = TopicPlugin.Topic()
        generate.create!!.kafkaUsers = mapOf("crypto" to "Chris", "db" to "Dan", "flow" to "Fiona", "membership" to "Mo")
        return generate
    }
}