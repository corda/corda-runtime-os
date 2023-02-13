package net.corda.p2p.gateway.messaging.http

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPipeline
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.ssl.SslHandler
import io.netty.handler.timeout.IdleStateHandler
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.InetSocketAddress
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.X509ExtendedKeyManager

class HttpServerTest {
    private val listener = mock<HttpServerListener>()
    private val configuration = GatewayConfiguration(
        hostAddress = "www.r3.com",
        hostPort = 33,
        urlPath = "/",
        sslConfig = mock(),
        maxRequestSize = 1_000
    )
    private val address = InetSocketAddress("www.r3.com", 30)
    private val channel = mock<Channel> {
        on { remoteAddress() } doReturn address
    }
    private val serverChannel = mock<Channel>()
    private val serverChannelInitializer = argumentCaptor<ChannelInitializer<SocketChannel>>()
    private val groups = mutableListOf<NioEventLoopGroup>()

    private val keyManagerFactory = mockStatic(KeyManagerFactory::class.java).also {
        val factory = mock<KeyManagerFactory> {
            on { keyManagers } doReturn arrayOf(mock<X509ExtendedKeyManager>())
        }
        it.`when`<KeyManagerFactory> {
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        }.doReturn(factory)
    }
    private val serverBootstrap = mockConstruction(ServerBootstrap::class.java) { mock, _ ->
        val channelFuture = mock<ChannelFuture> {
            on { sync() } doReturn this.mock
            on { channel() } doReturn serverChannel
        }
        whenever(mock.group(any<NioEventLoopGroup>(), any<NioEventLoopGroup>())).doReturn(mock)
        whenever(mock.channel(NioServerSocketChannel::class.java)).doReturn(mock)
        whenever(mock.childHandler(serverChannelInitializer.capture())).doReturn(mock)
        whenever(mock.bind(any<String>(), any())) doReturn channelFuture
    }
    private val groupFactory = mockConstruction(NioEventLoopGroup::class.java) { mock, _ ->
        whenever(mock.terminationFuture()).doReturn(mock())
        groups.add(mock)
    }

    @AfterEach
    fun cleanUp() {
        serverBootstrap.close()
        keyManagerFactory.close()
        groupFactory.close()
    }

    private val server = HttpServer(listener, configuration, KeyStoreWithPassword(mock(), ""), null)

    @Test
    fun `write will throw an exception if the channel is not opened`() {
        assertThrows<IllegalStateException> {
            server.write(
                HttpResponseStatus.ACCEPTED,
                byteArrayOf(1),
                address
            )
        }
    }

    @Test
    fun `write will write to an open channel`() {
        val response = argumentCaptor<DefaultFullHttpResponse>()
        val writeFuture = mock<ChannelFuture>()
        whenever(channel.writeAndFlush(response.capture())).doReturn(writeFuture)
        server.onOpen(HttpConnectionEvent(channel))

        server.write(
            HttpResponseStatus.OK,
            byteArrayOf(5, 7),
            address
        )

        assertSoftly {
            it.assertThat(response.firstValue.content().array()).isEqualTo(byteArrayOf(5, 7))
            it.assertThat(response.firstValue.status()).isEqualTo(HttpResponseStatus.OK)
        }
        verify(writeFuture, never()).addListener(ChannelFutureListener.CLOSE)
    }

    @Test
    fun `write will write to an open channel and close the channel if request failed`() {
        val response = argumentCaptor<DefaultFullHttpResponse>()
        val writeFuture = mock<ChannelFuture>()
        whenever(channel.writeAndFlush(response.capture())).doReturn(writeFuture)
        server.onOpen(HttpConnectionEvent(channel))

        server.write(
            HttpResponseStatus.BAD_REQUEST,
            byteArrayOf(5, 7),
            address
        )

        assertSoftly {
            it.assertThat(response.firstValue.content().array()).isEqualTo(byteArrayOf(5, 7))
            it.assertThat(response.firstValue.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST)
        }
        verify(writeFuture).addListener(ChannelFutureListener.CLOSE)
    }

    @Test
    fun `write will throw an exception if the channel is closed`() {
        server.onOpen(HttpConnectionEvent(channel))
        server.onClose(HttpConnectionEvent(channel))

        assertThrows<IllegalStateException> {
            server.write(
                HttpResponseStatus.ACCEPTED,
                byteArrayOf(1),
                address
            )
        }
    }

    @Test
    fun `onOpen will propagate the event`() {
        server.onOpen(HttpConnectionEvent(channel))

        verify(listener).onOpen(HttpConnectionEvent(channel))
    }

    @Test
    fun `onClose will propagate the event`() {
        server.onClose(HttpConnectionEvent(channel))

        verify(listener).onClose(HttpConnectionEvent(channel))
    }

    @Test
    fun `onMessage will propagate the event`() {
        val request = mock<HttpRequest>()
        server.onRequest(request)

        verify(listener).onRequest(request)
    }

    @Test
    fun `start will bind to the address`() {
        server.start()

        verify(serverBootstrap.constructed().first()).bind("www.r3.com", 33)
    }

    @Test
    fun `start will setup server correctly`() {
        server.start()

        verify(serverBootstrap.constructed().first()).group(groups[0], groups[1])
        verify(serverBootstrap.constructed().first()).channel(NioServerSocketChannel::class.java)
    }

    @Test
    fun `second start will do nothing`() {
        server.start()
        server.start()

        verify(serverBootstrap.constructed().first(), times(1)).bind("www.r3.com", 33)
    }

    @Test
    fun `isRunning will be true after start`() {
        server.start()

        assertThat(server.isRunning).isTrue
    }

    @Test
    fun `isRunning will be false when not started`() {
        assertThat(server.isRunning).isFalse
    }

    @Test
    fun `stop will terminate all the groups`() {
        server.start()

        server.close()

        verify(groups[0]).shutdownGracefully()
        verify(groups[1]).shutdownGracefully()
    }

    @Test
    fun `stop will close the server if open`() {
        server.start()
        whenever(serverChannel.isOpen).doReturn(true)
        whenever(serverChannel.close()).doReturn(mock())

        server.close()

        verify(serverChannel).close()
    }

    @Test
    fun `stop will not close the server if closed`() {
        server.start()
        whenever(serverChannel.isOpen).doReturn(false)

        server.close()

        verify(serverChannel, times(0)).close()
    }

    @Test
    fun `stop will ignore error during closing`() {
        server.start()
        whenever(serverChannel.isOpen).doReturn(true)
        whenever(serverChannel.close()).doThrow(RuntimeException(""))

        assertDoesNotThrow {
            server.close()
        }
    }

    @Test
    fun `stop will remove the address from the list`() {
        server.start()
        server.onOpen(HttpConnectionEvent(channel))
        server.close()

        assertThrows<IllegalStateException> {
            server.write(
                HttpResponseStatus.ACCEPTED,
                byteArrayOf(1),
                address
            )
        }
    }

    @Test
    fun `initChannel in ServerChannelInitializer will add handler to the pipeline`() {
        server.start()
        val pipeline = mock<ChannelPipeline>()
        val channel = mock<SocketChannel> {
            on { pipeline() } doReturn pipeline
        }
        val context = mock<ChannelHandlerContext> {
            on { channel() } doReturn channel
            on { pipeline() } doReturn pipeline
            on { executor() } doReturn mock()
        }

        serverChannelInitializer.firstValue.channelRegistered(context)

        verify(pipeline).addLast(eq("sslHandler"), any<SslHandler>())
        verify(pipeline).addLast(eq("idleStateHandler"), any<IdleStateHandler>())
        verify(pipeline).addLast(any<HttpServerCodec>())
        verify(pipeline).addLast(any<HttpServerChannelHandler>())
    }
}
