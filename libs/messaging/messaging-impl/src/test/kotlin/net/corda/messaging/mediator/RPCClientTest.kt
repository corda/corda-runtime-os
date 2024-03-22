package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.core.SecureHashImpl
import net.corda.data.flow.event.FlowEvent
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_ENDPOINT
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_KEY
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class RPCClientTest {

    private lateinit var client: RPCClient
    private val secureHash = SecureHashImpl("alg", "abc".toByteArray())
    private val payload = "testPayload".toByteArray()
    private val message = MediatorMessage(
        payload,
        mutableMapOf(
            MSG_PROP_ENDPOINT to "http://test-endpoint/api/5.1/test",
            MSG_PROP_KEY to "test"
        )
    )

    data class Mocks(
        val serializer: CordaAvroSerializer<Any>,
        val deserializer: CordaAvroDeserializer<Any>,
        val httpClient: HttpClient,
        val httpResponse: HttpResponse<ByteArray>,
        val digestService: PlatformDigestService
    )

    private inner class MockEnvironment(
        val mockSerializer: CordaAvroSerializer<Any> = mock(),
        val mockDeserializer: CordaAvroDeserializer<Any> = mock(),
        val mockHttpClient: HttpClient = mock(),
        val mockHttpResponse: HttpResponse<ByteArray> = mock(),
        val mockDigestService: PlatformDigestService = mock()
    ) {
        init {
            whenever(mockSerializer.serialize(any<Record<*, *>>()))
                .thenReturn("testPayload".toByteArray())

            whenever(mockDeserializer.deserialize(any()))
                .thenReturn(FlowEvent())

            whenever(mockHttpResponse.statusCode())
                .thenReturn(200)

            whenever(mockHttpResponse.body())
                .thenReturn("responsePayload".toByteArray())

            whenever(mockHttpClient.send(any(), any<HttpResponse.BodyHandler<*>>()))
                .thenReturn(mockHttpResponse)

            whenever(mockDigestService.hash(any<ByteArray>(), any())).thenReturn(secureHash)
        }

        fun setResponse(bytes: ByteArray) = apply {
            whenever(mockHttpResponse.body())
                .thenReturn(bytes)
        }

        fun withHttpStatus(status: Int) = apply {
            whenever(mockHttpResponse.statusCode()).thenReturn(status)
        }

        val mocks: Mocks
            get() = Mocks(mockSerializer, mockDeserializer, mockHttpClient, mockHttpResponse, mockDigestService)
    }


    private fun createClient(
        mocks: Mocks,
        onSerializationError: (ByteArray) -> Unit = mock(),
    ): RPCClient {
        val mockSerializationFactory: CordaAvroSerializationFactory = mock()

        whenever(mockSerializationFactory.createAvroSerializer<Any>(any()))
            .thenReturn(mocks.serializer)

        whenever(mockSerializationFactory.createAvroDeserializer(any(), eq(Any::class.java)))
            .thenReturn(mocks.deserializer)

        return RPCClient(
            "TestRPCClient1",
            mockSerializationFactory,
            mocks.digestService,
            onSerializationError,
            mocks.httpClient
        )
    }

    @BeforeEach
    fun setup() {
        val environment = MockEnvironment()
        client = createClient(environment.mocks)
    }

    @Test
    fun `send() processes message and returns result`() {
        val result = client.send(message)
        assertNotNull(result?.payload)
        assertEquals(
            FlowEvent(),
            result!!.payload
        )
    }

    @Test
    fun `send() processes message and returns empty byte array`() {
        val environment = MockEnvironment()
            .setResponse(byteArrayOf())

        val client = createClient(environment.mocks)
        val result = client.send(message)
        assertNull(result)
    }

    @Test
    fun `send() handles 4XX error`() {
        val environment = MockEnvironment()
            .withHttpStatus(404)

        val client = createClient(environment.mocks)

        assertThrows<CordaMessageAPIFatalException> {
            client.send(message)
        }
    }

    @Test
    fun `send() handles 5XX error`() {
        val environment = MockEnvironment()
            .withHttpStatus(500)

        val client = createClient(environment.mocks)

        assertThrows<CordaMessageAPIFatalException> {
            client.send(message)
        }
    }

    @Test
    fun `send() handles 503 error`() {
        val environment = MockEnvironment()
            .withHttpStatus(503)

        val client = createClient(environment.mocks)

        assertThrows<CordaMessageAPIIntermittentException> {
            client.send(message)
        }
    }

    @Test
    fun `send() handles deserialization error`() {
        val onSerializationError: (ByteArray) -> Unit = mock()

        val environment = MockEnvironment().apply {
            whenever(mockDeserializer.deserialize(any()))
                .thenThrow(IllegalArgumentException("Deserialization error"))
        }

        val client = createClient(environment.mocks, onSerializationError)

        assertThrows<CordaMessageAPIFatalException> {
            client.send(message)
        }

        verify(onSerializationError).invoke(any())
    }

    @Test
    fun `send retries on IOException and eventually succeeds`() {
        val environment = MockEnvironment().apply {
            whenever(mockHttpClient.send(any(), any<HttpResponse.BodyHandler<*>>()))
                .thenThrow(IOException("Simulated IO exception"))
                .thenThrow(IOException("Simulated IO exception"))
                .thenReturn(mockHttpResponse)
        }

        val client = createClient(environment.mocks)
        val result = client.send(message)

        assertNotNull(result?.payload)
        assertEquals(
            FlowEvent(),
            result!!.payload
        )
    }

    @Test
    fun `send fails after exhausting all retries`() {
        val environment = MockEnvironment().apply {
            whenever(mockHttpClient.send(any(), any<HttpResponse.BodyHandler<*>>()))
                .thenThrow(IOException("Simulated IO exception"))
        }

        val client = createClient(environment.mocks)

        assertThrows<CordaMessageAPIIntermittentException> {
            client.send(message)
        }
    }

    @Test
    fun `send retries the correct number of times before failing`() {
        val environment = MockEnvironment().apply {
            whenever(mockHttpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<*>>()))
                .thenThrow(IOException("Simulated IO exception"))
        }

        val client = createClient(environment.mocks)

        assertThrows<CordaMessageAPIIntermittentException> {
            client.send(message)
        }

        verify(environment.mockHttpClient, times(3))
            .send(any<HttpRequest>(), any<HttpResponse.BodyHandler<*>>())
    }


    @Test
    fun `send processes messages and throws InterruptException and bubbles up to caller`() {
        val environment = MockEnvironment().apply {
            whenever(mockHttpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<*>>()))
                .thenThrow(InterruptedException("interrupted"))
        }
        client = createClient(environment.mocks)

        assertThrows<InterruptedException> {
            client.send(message)
        }
    }

}
