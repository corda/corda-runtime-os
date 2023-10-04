package net.corda.messaging.mediator

import java.io.IOException
import kotlinx.coroutines.runBlocking
import java.net.http.HttpClient
import java.net.http.HttpResponse
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RPCClientTest {

    private lateinit var client: RPCClient
    private val message = MediatorMessage(Record("topic", "key", "testPayload"))

    data class Mocks(
        val serializer: CordaAvroSerializer<Any>,
        val deserializer: CordaAvroDeserializer<Record<*,*>>,
        val httpClient: HttpClient,
        val httpResponse: HttpResponse<ByteArray>
    )

    private inner class MockEnvironment(
        val mockSerializer: CordaAvroSerializer<Any> = mock(),
        val mockDeserializer: CordaAvroDeserializer<Record<*,*>> = mock(),
        val mockHttpClient: HttpClient = mock(),
        val mockHttpResponse: HttpResponse<ByteArray> = mock()
    ) {
        init {
            whenever(mockSerializer.serialize(any<Record<*,*>>()))
                .thenReturn("testPayload".toByteArray())

            whenever(mockDeserializer.deserialize(any<ByteArray>()))
                .thenReturn(Record("topic", "key", "responsePayload"))

            whenever(mockHttpResponse.statusCode())
                .thenReturn(200)

            whenever(mockHttpResponse.body())
                .thenReturn("responsePayload".toByteArray())

            whenever(mockHttpClient.send(any(), any<HttpResponse.BodyHandler<*>>()))
                .thenReturn(mockHttpResponse)
        }

        fun withHttpStatus(status: Int) = apply {
            whenever(mockHttpResponse.statusCode()).thenReturn(status)
        }

        val mocks: Mocks
            get() = Mocks(mockSerializer, mockDeserializer, mockHttpClient, mockHttpResponse)
    }


    private fun createClient(
        mocks: Mocks,
        onSerializationError: (ByteArray) -> Unit = mock(),
        httpClientFactory: () -> HttpClient = { mocks.httpClient }
    ): RPCClient {
        val mockSerializationFactory: CordaAvroSerializationFactory = mock()

        whenever(mockSerializationFactory.createAvroSerializer<Any>(any()))
            .thenReturn(mocks.serializer)

        whenever(mockSerializationFactory.createAvroDeserializer(any(), eq(Record::class.java)))
            .thenReturn(mocks.deserializer)

        return RPCClient(
            "TestRPCClient1",
            mockSerializationFactory,
            onSerializationError,
            httpClientFactory
        )
    }

    @BeforeEach
    fun setup() {
        val environment = MockEnvironment()
        client = createClient(environment.mocks)
    }

    @Test
    fun `send() processes message and returns result`() {
        runBlocking {
            val result = client.send(message).await()
            assertNotNull(result?.payload)
            assertEquals(
                Record("topic", "key", "responsePayload"),
                result!!.payload)
        }
    }

    @Test
    fun `send() handles 4XX error`() {
        val environment = MockEnvironment()
            .withHttpStatus(404)

        val client = createClient(environment.mocks)

        runBlocking {
            assertThrows<RPCClient.HttpClientErrorException> {
                client.send(message).await()
            }
        }
    }

    @Test
    fun `send() handles 5XX error`() {
        val environment = MockEnvironment()
            .withHttpStatus(500)

        val client = createClient(environment.mocks)

        runBlocking  {
            assertThrows<RPCClient.HttpServerErrorException> {
                client.send(message).await()
            }
        }
    }

    @Test
    fun `send() handles serialization error`() {
        val onSerializationError: (ByteArray) -> Unit = mock()

        val environment = MockEnvironment().apply {
            whenever(mockSerializer.serialize(any<Record<*,*>>()))
                .thenThrow(IllegalArgumentException("Serialization error"))
        }

        val client = createClient(environment.mocks, onSerializationError)

        runBlocking {
            assertThrows<Exception> {
                client.send(message).await()
            }

            verify(onSerializationError).invoke(any())
        }
    }

    @Test
    fun `send() handles deserialization error`() {
        val onSerializationError: (ByteArray) -> Unit = mock()

        val environment = MockEnvironment().apply {
            whenever(mockSerializer.serialize(any<Record<*,*>>()))
                .thenThrow(IllegalArgumentException("Deserialization error"))
        }

        val client = createClient(environment.mocks, onSerializationError)

        runBlocking {
            assertThrows<Exception> {
                client.send(message).await()
            }

            verify(onSerializationError).invoke(any())
        }
    }

    @Test
    fun `send handles IOException`() {
        val environment = MockEnvironment().apply {
            whenever(mockHttpClient.send(any(), any<HttpResponse.BodyHandler<*>>()))
                .thenThrow(IOException("Simulated IO exception"))
        }

        val client = createClient(environment.mocks)

        runBlocking {
            val deferred = client.send(message)
            assertThrows<IOException> {
                deferred.await()
            }
        }
    }
}
