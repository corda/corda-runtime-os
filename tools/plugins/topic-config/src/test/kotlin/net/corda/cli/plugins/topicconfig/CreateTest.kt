package net.corda.cli.plugins.topicconfig

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.net.URL
import java.util.jar.JarEntry
import java.util.jar.JarFile

class CreateTest {
    @Test
    fun `create config returns empty string with no entries`() {
        val plugin = Create()
        assertThat(plugin.createConfigString(emptyMap())).isEqualTo("")
    }

    @Test
    fun `create config returns a string containing all values`() {
        val plugin = Create()
        val config = mapOf(
            "one" to "a",
            "two" to "b",
            "three" to "c"
        )
        assertThat(plugin.createConfigString(config)).isEqualTo(
            "--config \"one=a\" --config \"two=b\" --config \"three=c\""
        )
    }

    @Test
    fun `validate create topic script output`() {
        val plugin = Create()
        plugin.bootstrapAddress = "address"

        val create1 = plugin.createTopicScripts("topic", 1, 1, emptyMap())
        assertThat(create1).containsExactly("kafka-topics.sh --bootstrap-server address --partitions 1 --replication-factor 1 --create --if-not-exists --topic topic  &")
        val create2 = plugin.createTopicScripts("topic", 1, 1, mapOf("test.key" to "test.val"))
        assertThat(create2).containsExactly("kafka-topics.sh --bootstrap-server address --partitions 1 --replication-factor 1 --create --if-not-exists --topic topic --config \"test.key=test.val\" &")
        val create3 = plugin.createTopicScripts("topic", 1, 1, mapOf("test.key" to "test.val", "something" to "else"))
        assertThat(create3).containsExactly("kafka-topics.sh --bootstrap-server address --partitions 1 --replication-factor 1 --create --if-not-exists --topic topic --config \"test.key=test.val\" --config \"something=else\" &")
    }

    @Test
    fun `check no address throws exception`() {
        val plugin = Create()

        assertThrows<NoValidBootstrapAddress> {
            plugin.createTopicScripts("topic", 1, 1, mapOf("test.key" to "test.val", "something" to "else"))
        }
    }

    @Test
    fun `validate create topic acls output`() {
        val plugin = Create()
        plugin.bootstrapAddress = "address"

        val create1 = plugin.createACLs("topic", emptyList(), emptyList())
        assertThat(create1).isEmpty()
        val create2 = plugin.createACLs("topic", listOf("one", "two"), emptyList())
        assertThat(create2).containsExactly(
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:one --operation read --topic topic",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:two --operation read --topic topic"
        )
        val create3 = plugin.createACLs("topic", emptyList(), listOf("one", "two"))
        assertThat(create3).containsExactly(
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:one --operation write --topic topic",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:two --operation write --topic topic"
        )
        val create4 = plugin.createACLs("topic", listOf("one", "two"), listOf("three", "four"))
        assertThat(create4).containsExactly(
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:one --operation read --topic topic",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:two --operation read --topic topic",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:three --operation write --topic topic",
            "kafka-acls.sh --bootstrap-server address --add --allow-principal User:four --operation write --topic topic"
        )
    }

    @Test
    fun `ensure collectJars excludes non jar files`() {
        val plugin = Create()
        val url = mock<URL> {
            on { path } doReturn "somePath"
            on { protocol } doReturn "notJar"
        }

        assertThat(plugin.collectJars(listOf(url))).isEqualTo(emptyList<JarFile>())
    }

    @Test
    fun `ensure resource extractor returns no resources without extensions provided`() {
        val plugin = Create()
        val mockEntry = mock<JarEntry> {
            on { name } doReturn "test.yaml"
        }
        val mockEntries: (JarFile) -> List<JarEntry> = { listOf(mockEntry) }

        @Suppress("UNCHECKED_CAST")
        val jar = mock<JarFile>()

        val resources = plugin.extractResourcesFromJars(emptyList(), emptyList(), jars=listOf(jar), getEntries=mockEntries)

        assertThat(resources).isEmpty()
    }
}
