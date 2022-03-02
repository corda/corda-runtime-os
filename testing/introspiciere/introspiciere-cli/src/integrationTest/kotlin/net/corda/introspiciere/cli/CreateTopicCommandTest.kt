package net.corda.introspiciere.cli

import com.jayway.jsonpath.JsonPath
import net.corda.introspiciere.junit.InMemoryIntrospiciereServer
import net.corda.introspiciere.junit.random8
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.KeyPairEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import kotlin.concurrent.thread

class CreateTopicCommandTest {

    companion object {
        @RegisterExtension
        @JvmStatic
        private val introspiciere = InMemoryIntrospiciereServer(
            // This only works locally at the moment. For CI it should read
            // this for an environment variable or from a config file
            // kafkaBrokers = getMinikubeKafkaBroker()
            kafkaBrokers = "20.62.51.171:9094"
        )
    }

    @Test
    fun `i can create a topic from cli`() {
        val topic = "topics".random8
        internalMain("topics", "create",
            "--endpoint", introspiciere.endpoint,
            "--topic", topic,
            "--partitions", "3",
            "--replication-factor", "2",
            "--config", "cleanup.policy=compact",
            "-c", "segment.ms=300000"
        )

        internalMain("topics", "describe", "--endpoint", introspiciere.endpoint, "--topic", topic)
    }

    @Test
    fun `write message from cli`() {
        val topic = "topic".random8
        val key = "key".random8

        val input = """
            {
                "keyAlgo": "ECDSA",
                "publicKey": "public-key",
                "privateKey": "private-key"
            }
        """.trimIndent()

        introspiciere.client.createTopic(topic)
        val sequence = introspiciere.client.readFromLatest<KeyPairEntry>(topic, key)

        input.byteInputStream().use {
            internalMain("write",
                "--endpoint", introspiciere.endpoint,
                "--topic", topic,
                "--key", key,
                "--schema", KeyPairEntry::class.qualifiedName!!,
                overrideStdin = it
            )
        }

        val message = sequence.first()
        assertNotNull(message, "Message cannot be null")
        assertEquals(KeyAlgorithm.ECDSA, message!!.keyAlgo, "Key algorithm")
        assertEquals(ByteBuffer.wrap("public-key".toByteArray()), message.publicKey, "Public key")
        assertEquals(ByteBuffer.wrap("private-key".toByteArray()), message.privateKey, "Private key")
    }

    @Test
    fun `read message from cli`() {
        val topic = "topic".random8
        val key = "key".random8
        val keyPairEntry = generateKeyPairEntry()

        introspiciere.client.createTopic(topic)
        introspiciere.client.write(topic, key, keyPairEntry)

        // End loop after 5 seconds
        val th = thread {
            Thread.sleep(5000)
            ReadCommand.exitCommandGracefullyInTesting()
        }

        val outputStream = ByteArrayOutputStream()
        internalMain("read",
            "--endpoint", introspiciere.endpoint,
            "--topic", topic,
            "--key", key,
            "--schema", KeyPairEntry::class.qualifiedName!!,
            "--from-beginning",
            overrideStdout = outputStream
        )

        th.join()

        val stdout = outputStream.toByteArray().let(::String)
        println("the stdout is $stdout")
        val jpath = JsonPath.parse(stdout)
        assertEquals("ECDSA", jpath.read("$.keyAlgo"))
        // TODO: Assert public and private keys. Don't know how to do it
    }

    private fun generateKeyPairEntry(): KeyPairEntry {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(571)
        val pair = generator.generateKeyPair()
        return KeyPairEntry(
            KeyAlgorithm.ECDSA,
            ByteBuffer.wrap(pair.public.encoded),
            ByteBuffer.wrap(pair.private.encoded)
        )
    }
}
