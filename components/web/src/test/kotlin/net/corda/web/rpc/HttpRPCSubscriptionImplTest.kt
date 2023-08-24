package net.corda.web.rpc

import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.web.server.JavalinServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq

class HttpRPCSubscriptionImplTest {

    private val webServer = JavalinServer(mock(LifecycleCoordinatorFactory::class.java))
    private val TEST_ENDPOINT = "/test"
    private val TEST_PORT = 7777
    private val INPUT = "Request String"

    private lateinit var rpcSubscription: HttpRPCSubscriptionImpl

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
    private val cordaAvroSerializationFactory = org.mockito.kotlin.mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<String>(any()) } doReturn serializer
        on { createAvroDeserializer(any(), eq(String::class.java)) } doReturn deserializer
    }

    @BeforeEach
    fun setup() {
        webServer.start(TEST_PORT)
        rpcSubscription = HttpRPCSubscriptionImpl(cordaAvroSerializationFactory, webServer)
    }

    @AfterEach
    fun teardownServer() {
        webServer.stop()
    }

    @Test
    fun `registerEndpoint should register endpoint and handle request`() {
        val sampleHandler: (String) -> String = { input ->
            "input: '$input', has been handled"
        }

        rpcSubscription.registerEndpoint(TEST_ENDPOINT, sampleHandler, String::class.java)

        val url = URL("http://localhost:$TEST_PORT$TEST_ENDPOINT")
        val client = HttpClient.newBuilder().build()
        val request = HttpRequest.newBuilder()
            .uri(url.toURI())
            .POST(HttpRequest.BodyPublishers.ofByteArray(INPUT.toByteArray()))
            .build();

        val resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        val code = resp.statusCode()
        val body = String(resp.body())

        assertEquals("input: '$INPUT', has been handled", body)
        assertEquals(200, code)
    }
}