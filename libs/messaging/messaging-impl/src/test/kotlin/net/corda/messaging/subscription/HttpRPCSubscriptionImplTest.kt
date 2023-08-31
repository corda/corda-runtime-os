package net.corda.messaging.subscription

import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.processor.HttpRPCProcessor
import net.corda.messaging.api.subscription.config.HttpRPCConfig
import net.corda.web.server.JavalinFactory
import net.corda.web.server.JavalinServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import kotlin.random.Random

class HttpRPCSubscriptionImplTest {

    private val lifecycleCoordinator = org.mockito.kotlin.mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = org.mockito.kotlin.mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) }.doReturn(lifecycleCoordinator)
    }

    private val webServer = JavalinServer(lifecycleCoordinatorFactory, JavalinFactory())
    private val TEST_ENDPOINT = "/test"
    private val TEST_PORT = Random(System.nanoTime()).nextInt(8081, 9000)
    private val INPUT = "Request String"

    private lateinit var rpcSubscription: HttpRPCSubscriptionImpl<String, String>

    private val serializer: CordaAvroSerializer<String> = object : CordaAvroSerializer<String> {
        override fun serialize(data: String): ByteArray? {
            return data.toByteArray()
        }
    }
    private val deserializer: CordaAvroDeserializer<String> = object : CordaAvroDeserializer<String> {
        override fun deserialize(data: ByteArray): String? {
            return String(data)
        }

    }

    @BeforeEach
    fun setup() {
        val processor = object : HttpRPCProcessor<String, String> {
            override fun process(request: String): String {
                return "input: '$request', has been handled"
            }

            override val reqClass: Class<String> = String::class.java
            override val respClass: Class<String> = String::class.java
        }

        webServer.start(TEST_PORT)
        rpcSubscription = HttpRPCSubscriptionImpl(
            HttpRPCConfig(
                TEST_ENDPOINT
            ), processor, lifecycleCoordinatorFactory, webServer, serializer, deserializer
        )
    }

    @AfterEach
    fun teardownServer() {
        webServer.stop()
    }

    @Test
    fun `starting the subscription should register endpoint and handle request`() {
        rpcSubscription.start()

        val url = URL("http://localhost:$TEST_PORT$TEST_ENDPOINT")
        val client = HttpClient.newBuilder().build()
        val request = HttpRequest.newBuilder()
            .uri(url.toURI())
            .POST(HttpRequest.BodyPublishers.ofByteArray(INPUT.toByteArray()))
            .build()

        val resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        val code = resp.statusCode()
        val body = String(resp.body())

        assertEquals("input: '$INPUT', has been handled", body)
        assertEquals(200, code)
    }
}