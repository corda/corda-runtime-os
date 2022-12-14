package net.corda.cli.plugins.topicconfig

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.net.URL
import java.util.jar.JarEntry
import java.util.jar.JarFile

class CreateScriptTest {
    @Test
    fun `create config returns empty string with no entries`() {
        val command = command()
        assertThat(command.createConfigString(emptyMap())).isEqualTo("")
    }

    @Test
    fun `create config returns a string containing all values`() {
        val command = command()
        val config = mapOf(
            "one" to "a",
            "two" to "b",
            "three" to "c"
        )
        assertThat(command.createConfigString(config)).isEqualTo(
            "--config \"one=a\" --config \"two=b\" --config \"three=c\""
        )
    }

    @Test
    fun `validate create topic script output`() {
        val command = command()
        command.create!!.topic!!.bootstrapServer = "address"
        command.create!!.topic!!.kafkaConfig = "/tmp/working_dir/config.properties"

        val create1 = command.createTopicScripts("topic", 1, 1, emptyMap())
        @Suppress("MaxLineLength")
        assertThat(create1).containsExactly("kafka-topics.sh --bootstrap-server address --command-config /tmp/working_dir/config.properties --partitions 1 --replication-factor 1 --create --if-not-exists --topic topic  &")
        val create2 = command.createTopicScripts("topic", 1, 1, mapOf("test.key" to "test.val"))
        @Suppress("MaxLineLength")
        assertThat(create2).containsExactly("kafka-topics.sh --bootstrap-server address --command-config /tmp/working_dir/config.properties --partitions 1 --replication-factor 1 --create --if-not-exists --topic topic --config \"test.key=test.val\" &")
        val create3 = command.createTopicScripts("topic", 1, 1, mapOf("test.key" to "test.val", "something" to "else"))
        @Suppress("MaxLineLength")
        assertThat(create3).containsExactly("kafka-topics.sh --bootstrap-server address --command-config /tmp/working_dir/config.properties --partitions 1 --replication-factor 1 --create --if-not-exists --topic topic --config \"test.key=test.val\" --config \"something=else\" &")
    }

    @Test
    fun `validate create topic acls output`() {
        val command = command()
        command.create!!.topic!!.bootstrapServer = "address"

        val create1 = command.createACLs("topic", emptyList(), emptyList())
        assertThat(create1).isEmpty()
        val create2 = command.createACLs("topic", listOf("db", "flow"), emptyList())
        assertThat(create2).containsExactly(
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:Dan --operation read --topic topic &",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:Dan --operation describe --topic topic &",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:Fiona --operation read --topic topic &",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:Fiona --operation describe --topic topic &"
        )
        val create3 = command.createACLs("topic", emptyList(), listOf("db", "flow"))
        assertThat(create3).containsExactly(
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:Dan --operation write --topic topic &",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:Dan --operation describe --topic topic &",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:Fiona --operation write --topic topic &",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:Fiona --operation describe --topic topic &"
        )
        val create4 = command.createACLs("topic", listOf("db", "flow"), listOf("crypto", "membership"))
        assertThat(create4).containsExactly(
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:Dan --operation read --topic topic &",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:Dan --operation describe --topic topic &",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:Fiona --operation read --topic topic &",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:Fiona --operation describe --topic topic &",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:Chris --operation write --topic topic &",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:Chris --operation describe --topic topic &",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:Mo --operation write --topic topic &",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:Mo --operation describe --topic topic &"
        )
    }

    @Test
    fun `ensure collectJars excludes non jar files`() {
        val command = command()
        val url = mock<URL> {
            on { path } doReturn "somePath"
            on { protocol } doReturn "notJar"
        }

        assertThat(command.create!!.collectJars(listOf(url))).isEqualTo(emptyList<JarFile>())
    }

    @Test
    fun `ensure resource extractor returns no resources without extensions provided`() {
        val command = command()
        val mockEntry = mock<JarEntry> {
            on { name } doReturn "test.yaml"
        }
        val mockEntries: (JarFile) -> List<JarEntry> = { listOf(mockEntry) }

        val jar = mock<JarFile>()

        val resources = command.create!!.extractResourcesFromJars(emptyList(), emptyList(), jars=listOf(jar), getEntries=mockEntries)

        assertThat(resources).isEmpty()
    }

    private fun command() : CreateScript {
        val createScript = CreateScript()
        createScript.create = Create()
        createScript.create!!.topic = TopicPlugin.Topic()
        createScript.create!!.kafkaUsers = mapOf("crypto" to "Chris", "db" to "Dan", "flow" to "Fiona", "membership" to "Mo")
        return createScript
    }
}
