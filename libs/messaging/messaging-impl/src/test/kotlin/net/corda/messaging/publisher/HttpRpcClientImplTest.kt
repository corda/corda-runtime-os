package net.corda.messaging.publisher

import net.corda.messaging.api.publisher.send
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration
import java.util.Date
import java.util.UUID

class HttpRpcClientImplTest {
    private val request = UUID(0, 1)
    private val expectedResponse = Date(100L)
    private val serializedRequest = byteArrayOf(1, 2, 3)
    private val serializedResponse = byteArrayOf(4, 5)
    private val avroSchemaRegistry = mock<AvroSchemaRegistry> {
        on { serialize(request) } doReturn ByteBuffer.wrap(serializedRequest)
        on {
            deserialize(ByteBuffer.wrap(serializedResponse), Date::class.java, null)
        } doReturn expectedResponse
    }
    private val httpRequest = mock<HttpRequest>()
    private val httpResponse = mock<HttpResponse<ByteArray>> {
        on { statusCode() } doReturn 200
        on { body() } doReturn serializedResponse
    }
    private val httpClient = mock<HttpClient> {
        on { send(eq(httpRequest), isA<HttpResponse.BodyHandler<ByteArray>>()) } doReturn httpResponse
    }
    private val uri = URI.create("http://corda.net/test")
    private val requestBuilder = mock<HttpRequest.Builder> {
        on { uri(uri) } doReturn mock
        on { timeout(any()) } doReturn mock
        on { POST(any()) } doReturn mock
        on { build() } doReturn httpRequest
    }

    private val sleepers = mutableListOf<Long>()
    private val client = HttpRpcClientImpl(
        avroSchemaRegistry,
        httpClient,
        { requestBuilder },
        { sleepers.add(it) },
    )

    @Test
    fun `send return the correct data`() {
        val response: Date? = client.send(uri, request)

        assertThat(response).isEqualTo(expectedResponse)
    }

    @Test
    fun `send return the null if null is returned`() {
        whenever(httpResponse.body()).doReturn(null)
        val response: Date? = client.send(uri, request)

        assertThat(response).isNull()
    }

    @Test
    fun `send return the null if emptyAray is returned`() {
        whenever(httpResponse.body()).doReturn(byteArrayOf())
        val response: Date? = client.send(uri, request)

        assertThat(response).isNull()
    }

    @Test
    fun `send set the correct timeout`() {
        val timout = argumentCaptor<Duration>()
        whenever(requestBuilder.timeout(timout.capture())).doReturn(requestBuilder)

        client.send<Date>(uri, request)

        assertThat(timout.firstValue).isEqualTo(Duration.ofSeconds(30))
    }

    @Test
    fun `send post the correct data`() {
        val body = argumentCaptor<HttpRequest.BodyPublisher>()
        whenever(requestBuilder.POST(body.capture())).doReturn(requestBuilder)

        client.send<Date>(uri, request)

        assertThat(body.firstValue.contentLength()).isEqualTo(serializedRequest.size.toLong())
    }

    @Test
    fun `send with error throws exception`() {
        whenever(httpResponse.statusCode()).doReturn(500)

        assertThrows<CordaRuntimeException> {
            client.send<Date>(uri, request)
        }
    }

    @Test
    fun `send with error and success will return data`() {
        whenever(httpResponse.statusCode()).doReturnConsecutively(listOf(500, 700, 200))

        val response: Date? = client.send(uri, request)

        assertThat(response).isEqualTo(expectedResponse)
    }

    @Test
    fun `send with wait before retrying`() {
        whenever(httpResponse.statusCode()).doReturn(400)

        assertThrows<CordaRuntimeException> {
            client.send<Date>(uri, request)
        }

        assertThat(sleepers).containsExactly(100, 200, 400)
    }

    @Test
    fun `send with IO exception will wrap the exception`() {
        whenever(httpClient.send(eq(httpRequest), isA<HttpResponse.BodyHandler<ByteArray>>())).doThrow(IOException("oops"))

        assertThrows<CordaRuntimeException> {
            client.send<Date>(uri, request)
        }
    }
}
