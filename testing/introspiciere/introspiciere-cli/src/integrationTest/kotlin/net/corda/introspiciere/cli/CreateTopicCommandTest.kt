package net.corda.introspiciere.cli

import com.jayway.jsonpath.JsonPath
import io.javalin.Javalin
import net.corda.introspiciere.junit.InMemoryIntrospiciereServer
import net.corda.introspiciere.junit.random8
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.KeyPairEntry
import net.corda.testdoubles.StandardStreams
import net.corda.testdoubles.inMemoryStdout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import kotlin.concurrent.thread

class FakeHttpServer(private val port: Int = 0) : BeforeAllCallback, BeforeEachCallback, AfterEachCallback,
    AfterAllCallback {

    lateinit var app: Javalin

    private var startedAtBeforeAll: Boolean = false
    private var startedAtBeforeEach: Boolean = false

    val endpoint: String
        get() = "http://localhost:${app.port()}"

    override fun beforeAll(context: ExtensionContext?) {
        start()
        startedAtBeforeAll = true
    }

    override fun beforeEach(context: ExtensionContext?) {
        if (startedAtBeforeAll) return
        start()
        startedAtBeforeEach = true
    }

    override fun afterEach(context: ExtensionContext?) {
        if (startedAtBeforeEach) stop()
        startedAtBeforeEach = false
    }

    override fun afterAll(context: ExtensionContext?) {
        if (startedAtBeforeAll) stop()
        startedAtBeforeAll = false
    }

    fun start() {
        app = Javalin.create()
        app.start(port)
    }

    fun stop() {
        app.stop()
    }

    fun getReturnsJson(path: String, any: Any): FakeHttpServer {
        app.get(path) { it.json(any) }
        return this
    }
}

@StandardStreams
class TopicCommandsTest {

    @RegisterExtension
    val server = FakeHttpServer()

    @Test
    fun `list all topics`() {
        server.getReturnsJson("/topics", setOf("topic1", "topic2"))
        internalMain("topics", "list", "--endpoint", server.endpoint, overrideStdout = inMemoryStdout.output)
        assertEquals("topic1\ntopic2\n", inMemoryStdout.readText())
    }

    @Test
    fun `list all topics - empty`() {
        server.getReturnsJson("/topics", emptySet<String>())
        internalMain("topics", "list", "--endpoint", server.endpoint, overrideStdout = inMemoryStdout.output)
        assertEquals("", inMemoryStdout.readText())
    }
}

@Suppress("UNUSED_VARIABLE")
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
