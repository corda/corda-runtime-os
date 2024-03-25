package net.corda.p2p.gateway.messaging.http

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.EmptyHttpHeaders
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import net.corda.p2p.gateway.messaging.http.HttpHelper.Companion.validate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class HttpHelperTest {

    companion object {
        private const val MAX_REQUEST_SIZE = 10_000L
        private const val URL_PATH = "/gateway/send"
    }

    @Test
    fun `request with invalid uri fails validation`() {
        val uri = "https://www.alice.net:8080/wrong/path"
        val payload = mock<ByteBuf> {
            on { isReadable } doReturn true
        }
        val headers = DefaultHttpHeaders()
        headers.set(HttpHeaderNames.CONTENT_LENGTH, "100")
        headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri, payload, headers, EmptyHttpHeaders.INSTANCE)

        val status = request.validate(MAX_REQUEST_SIZE, listOf(URL_PATH, "/"))
        assertThat(status).isEqualTo(HttpResponseStatus.NOT_FOUND)
    }

    @Test
    fun `request with invalid http version fails validation`() {
        val uri = "https://www.alice.net:8080$URL_PATH"
        val payload = mock<ByteBuf> {
            on { isReadable } doReturn true
        }
        val headers = DefaultHttpHeaders()
        headers.set(HttpHeaderNames.CONTENT_LENGTH, "100")
        headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.POST, uri, payload, headers, EmptyHttpHeaders.INSTANCE)

        val status = request.validate(MAX_REQUEST_SIZE, listOf(URL_PATH))
        assertThat(status).isEqualTo(HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED)
    }

    @Test
    fun `request with invalid http method fails validation`() {
        val uri = "https://www.alice.net:8080$URL_PATH"
        val payload = mock<ByteBuf> {
            on { isReadable } doReturn true
        }
        val headers = DefaultHttpHeaders()
        headers.set(HttpHeaderNames.CONTENT_LENGTH, "100")
        headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri, payload, headers, EmptyHttpHeaders.INSTANCE)

        val status = request.validate(MAX_REQUEST_SIZE, listOf(URL_PATH))
        assertThat(status).isEqualTo(HttpResponseStatus.NOT_IMPLEMENTED)
    }

    @Test
    fun `request with invalid content type fails validation`() {
        val uri = "https://www.alice.net:8080$URL_PATH"
        val payload = mock<ByteBuf> {
            on { isReadable } doReturn true
        }
        val headers = DefaultHttpHeaders()
        headers.set(HttpHeaderNames.CONTENT_LENGTH, "100")
        headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_XML)
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri, payload, headers, EmptyHttpHeaders.INSTANCE)

        val status = request.validate(MAX_REQUEST_SIZE, listOf(URL_PATH))
        assertThat(status).isEqualTo(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE)
    }

    @Test
    fun `request without content length header fails validation`() {
        val uri = "https://www.alice.net:8080$URL_PATH"
        val payload = mock<ByteBuf> {
            on { isReadable } doReturn true
        }
        val headers = DefaultHttpHeaders()
        headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri, payload, headers, EmptyHttpHeaders.INSTANCE)

        val status = request.validate(MAX_REQUEST_SIZE, listOf(URL_PATH))
        assertThat(status).isEqualTo(HttpResponseStatus.LENGTH_REQUIRED)
    }

    @Test
    fun `request with content length over limit fails validation`() {
        val uri = "https://www.alice.net:8080$URL_PATH"
        val payload = mock<ByteBuf> {
            on { isReadable } doReturn true
        }
        val headers = DefaultHttpHeaders()
        headers.set(HttpHeaderNames.CONTENT_LENGTH, (MAX_REQUEST_SIZE + 1).toString())
        headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri, payload, headers, EmptyHttpHeaders.INSTANCE)

        val status = request.validate(MAX_REQUEST_SIZE, listOf(URL_PATH))
        assertThat(status).isEqualTo(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE)
    }

    @Test
    fun `valid request passes validation`() {
        val uri = "https://www.alice.net:8080$URL_PATH"
        val payload = mock<ByteBuf> {
            on { isReadable } doReturn true
        }
        val headers = DefaultHttpHeaders()
        headers.set(HttpHeaderNames.CONTENT_LENGTH, "100")
        headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri, payload, headers, EmptyHttpHeaders.INSTANCE)

        val status = request.validate(MAX_REQUEST_SIZE, listOf("/one", URL_PATH, "/two"))
        assertThat(status).isEqualTo(HttpResponseStatus.OK)
    }
}
