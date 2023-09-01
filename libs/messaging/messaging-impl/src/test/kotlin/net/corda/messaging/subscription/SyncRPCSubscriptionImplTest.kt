package net.corda.messaging.subscription

import io.javalin.Javalin
import java.net.ServerSocket
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.messaging.api.subscription.config.SyncRPCConfig
import net.corda.web.server.JavalinServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class SyncRPCSubscriptionImplTest {

    private val lifecycleCoordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) }.doReturn(lifecycleCoordinator)
    }

    private val webServer = JavalinServer(lifecycleCoordinatorFactory) { Javalin.create() }
    private val TEST_ENDPOINT = "/test"
    private val TEST_PORT = ServerSocket(0).use {
        it.localPort
    }
    private val INPUT = "Request String"

    private lateinit var rpcSubscription: SyncRPCSubscriptionImpl<String, String>

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
        val processor = object : SyncRPCProcessor<String, String> {
            override fun process(request: String): String {
                return "input: '$request', has been handled"
            }

            override val requestClass: Class<String> = String::class.java
            override val responseClass: Class<String> = String::class.java
        }

        webServer.start(TEST_PORT)
        rpcSubscription = SyncRPCSubscriptionImpl(
            SyncRPCConfig(
                TEST_ENDPOINT
            ), processor, lifecycleCoordinatorFactory, webServer, serializer, deserializer
        )

        SyncRPCSubscriptionImpl(
            SyncRPCConfig(
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