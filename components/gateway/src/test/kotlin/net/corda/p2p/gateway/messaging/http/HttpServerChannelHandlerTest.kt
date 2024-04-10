package net.corda.p2p.gateway.messaging.http

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.EmptyHttpHeaders
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.slf4j.Logger
import java.net.InetSocketAddress
import io.netty.handler.codec.http.HttpRequest as NettyHttpRequest

class HttpServerChannelHandlerTest {
    companion object {
        private const val MAX_REQUEST_SIZE = 500_000_000L
        private const val URL_PATH = "/gateway/send"
    }

    @Test
    fun `when request is invalid, a response with the right status code is returned and connection is closed eagerly`() {
        var waitOnWrite = false
        var waitOnClose = false

        val mockServerListener = mock<HttpServerListener>()
        val mockLogger = mock<Logger>()
        val httpServerChannelHandler = HttpServerChannelHandler(mockServerListener, MAX_REQUEST_SIZE, listOf(URL_PATH), mockLogger)

        val socketAddress = InetSocketAddress("www.alice.net", 91)
        val mockCtxChannel = mock<Channel> {
            on { remoteAddress() } doReturn socketAddress
        }
        val mockWriteFuture = mock<ChannelFuture> {
            on { get() } doAnswer {
                waitOnWrite = true
                mock()
            }
        }
        val mockCloseFuture = mock<ChannelFuture> {
            on { get() } doAnswer {
                waitOnClose = true
                mock()
            }
        }
        val mockCtx = mock<ChannelHandlerContext> {
            on { channel() } doReturn mockCtxChannel
            on { writeAndFlush(any()) } doReturn mockWriteFuture
            on { close() } doReturn mockCloseFuture
        }

        val mockHeaders = mock<HttpHeaders>()
        val mockHttpRequest = mock<NettyHttpRequest> {
            on { uri() } doReturn "http://www.alice.net:90"
            on { headers() } doReturn mockHeaders
        }

        val responseCaptor = argumentCaptor<io.netty.handler.codec.http.HttpResponse>()

        httpServerChannelHandler.channelRead(mockCtx, mockHttpRequest)

        verify(mockCtx).writeAndFlush(responseCaptor.capture())
        assertThat(waitOnWrite).isTrue
        assertThat(responseCaptor.firstValue.status()).isEqualTo(HttpResponseStatus.NOT_FOUND)
        verify(mockCtx).close()
        assertThat(waitOnClose).isTrue
    }

    @Test
    fun `when request is valid, data are sent to the http server listener for processing`() {
        val mockServerListener = mock<HttpServerListener>()
        val mockLogger = mock<Logger>()
        val httpServerChannelHandler = HttpServerChannelHandler(mockServerListener, MAX_REQUEST_SIZE, listOf(URL_PATH), mockLogger)

        val uri = "https://www.alice.net:8080$URL_PATH"
        val payload = mock<ByteBuf> {
            on { isReadable } doReturn true
        }
        val remoteAddress = InetSocketAddress("bob.net", 91)
        val localAddress = InetSocketAddress("alice.net", 90)
        val mockCtxChannel = mock<Channel> {
            on { localAddress() } doReturn localAddress
            on { remoteAddress() } doReturn remoteAddress
        }
        val handlerByteBuf = mock<ByteBuf>()
        val byteBufAllocator = mock<ByteBufAllocator> {
            on { buffer(any(), any()) } doReturn handlerByteBuf
        }
        val mockCtx = mock<ChannelHandlerContext> {
            on { channel() } doReturn mockCtxChannel
            on { alloc() } doReturn byteBufAllocator
        }

        val headers = DefaultHttpHeaders()
        headers.set(HttpHeaderNames.CONTENT_LENGTH, "100")
        headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri, payload, headers, EmptyHttpHeaders.INSTANCE)
        httpServerChannelHandler.channelRead(mockCtx, request)

        val requestCaptor = argumentCaptor<HttpRequest>()
        verify(handlerByteBuf).writeBytes(payload)
        verify(mockServerListener).onRequest(requestCaptor.capture())
        assertThat(requestCaptor.firstValue.destination).isEqualTo(localAddress)
        assertThat(requestCaptor.firstValue.source).isEqualTo(remoteAddress)
        verify(handlerByteBuf).readBytes(requestCaptor.firstValue.payload)
    }

    @Test
    fun `when body content is larger than what specified in content length header, 400 is returned and the connection is closed`() {
        var waitOnWrite = false
        var waitOnClose = false
        val mockServerListener = mock<HttpServerListener>()
        val mockLogger = mock<Logger>()
        val httpServerChannelHandler = HttpServerChannelHandler(mockServerListener, MAX_REQUEST_SIZE, listOf(URL_PATH), mockLogger)

        val uri = "https://www.alice.net:8080$URL_PATH"
        val payload = mock<ByteBuf> {
            on { isReadable } doReturn true
        }
        val remoteAddress = InetSocketAddress("bob.net", 91)
        val localAddress = InetSocketAddress("alice.net", 90)
        val mockCtxChannel = mock<Channel> {
            on { localAddress() } doReturn localAddress
            on { remoteAddress() } doReturn remoteAddress
        }
        val handlerByteBuf = mock<ByteBuf> {
            on { writeBytes(any<ByteBuf>()) } doThrow java.lang.IndexOutOfBoundsException()
        }
        val byteBufAllocator = mock<ByteBufAllocator> {
            on { buffer(any(), any()) } doReturn handlerByteBuf
        }
        val mockWriteFuture = mock<ChannelFuture> {
            on { get() } doAnswer {
                waitOnWrite = true
                mock()
            }
        }
        val mockCloseFuture = mock<ChannelFuture> {
            on { get() } doAnswer {
                waitOnClose = true
                mock()
            }
        }
        val mockCtx = mock<ChannelHandlerContext> {
            on { channel() } doReturn mockCtxChannel
            on { alloc() } doReturn byteBufAllocator
            on { writeAndFlush(any()) } doReturn mockWriteFuture
            on { close() } doReturn mockCloseFuture
        }

        val headers = DefaultHttpHeaders()
        headers.set(HttpHeaderNames.CONTENT_LENGTH, "100")
        headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri, payload, headers, EmptyHttpHeaders.INSTANCE)
        httpServerChannelHandler.channelRead(mockCtx, request)

        val responseCaptor = argumentCaptor<io.netty.handler.codec.http.HttpResponse>()
        verify(mockCtx).writeAndFlush(responseCaptor.capture())
        assertThat(waitOnWrite).isTrue
        assertThat(responseCaptor.firstValue.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST)
        verify(mockCtx).close()
        assertThat(waitOnClose).isTrue
    }
}
