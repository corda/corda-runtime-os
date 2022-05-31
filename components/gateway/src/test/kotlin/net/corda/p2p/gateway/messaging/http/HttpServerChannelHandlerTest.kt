package net.corda.p2p.gateway.messaging.http

import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.LastHttpContent
import io.netty.handler.codec.http.HttpRequest as NettyHttpRequest
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.atMost
import org.mockito.kotlin.mock
import org.slf4j.Logger
import java.net.InetSocketAddress
import java.net.SocketAddress

class HttpServerChannelHandlerTest {

    @Test
    fun `ChannelFutureListener is added`() {
        val mockServerListener = mock<HttpServerListener>()
        val mockLogger = mock<Logger>()
        val httpServerChannelHandler = HttpServerChannelHandler(mockServerListener, mockLogger)

        val mockCtx = mock<ChannelHandlerContext>()
        //val mockCtxChannel = mockCtx.channel()
        val mockChannelFuture = mock<ChannelFuture>()
        val mockLastHttpContent = mock<LastHttpContent>()
        val mockHttpRequest = mock<NettyHttpRequest>()
        val socketAddress = InetSocketAddress("www.alice.net", 91)

/*        val mockHttpRequest = mock<NettyHttpRequest> {
            on { uri() } doReturn "http://www.alice.net:90"
        }*/


        Mockito.`when`(mockHttpRequest.uri()).thenReturn("http://www.alice.net:90")
        Mockito.`when`(mockCtx.channel().remoteAddress()).thenReturn(socketAddress)
        //Mockito.`when`(mockCtxChannel.remoteAddress()).thenReturn(socketAddress)
        httpServerChannelHandler.channelRead(mockCtx, mockHttpRequest)
        Mockito.`when`(mockCtx.writeAndFlush(any())).thenReturn(mockChannelFuture)
        httpServerChannelHandler.channelRead(mockCtx, mockLastHttpContent)
        Mockito.verify(mockChannelFuture, atMost(1)).addListener { ChannelFutureListener.CLOSE }
    }
}