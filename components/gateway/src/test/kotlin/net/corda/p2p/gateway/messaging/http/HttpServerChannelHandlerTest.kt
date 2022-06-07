package net.corda.p2p.gateway.messaging.http

import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.EmptyByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.LastHttpContent
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atMost
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.Logger
import java.net.InetSocketAddress
import io.netty.handler.codec.http.HttpRequest as NettyHttpRequest

class HttpServerChannelHandlerTest {

    @Test
    fun `ChannelFutureListener is added`() {
        val mockServerListener = mock<HttpServerListener>()
        val mockLogger = mock<Logger>()
        val httpServerChannelHandler = HttpServerChannelHandler(mockServerListener, mockLogger)

        val socketAddress = InetSocketAddress("www.alice.net", 91)
        val mockCtxChannel = mock<Channel> {
            on { remoteAddress() } doReturn socketAddress
        }

        val mockChannelFuture = mock<ChannelFuture>()
        val mockCtx = mock<ChannelHandlerContext>{
            on { channel() } doReturn mockCtxChannel
            on { writeAndFlush(any()) } doReturn mockChannelFuture
        }

        val mockHeaders = mock<HttpHeaders>()
        val mockHttpRequest = mock<NettyHttpRequest> {
            on { uri() } doReturn "http://www.alice.net:90"
            on { headers() } doReturn mockHeaders
        }

        val mockLastHttpContent = mock<LastHttpContent> {
            on { content() } doReturn EmptyByteBuf(ByteBufAllocator.DEFAULT)
        }

        val listenerCaptor = argumentCaptor<ChannelFutureListener>()
        whenever(mockChannelFuture.addListener(listenerCaptor.capture())).doReturn(mockChannelFuture)

        httpServerChannelHandler.channelRead(mockCtx, mockHttpRequest)
        httpServerChannelHandler.channelRead(mockCtx, mockLastHttpContent)

        listenerCaptor.firstValue.operationComplete(mockChannelFuture)
        verify(listenerCaptor).firstValue.equals(ChannelFutureListener.CLOSE)
    }
}