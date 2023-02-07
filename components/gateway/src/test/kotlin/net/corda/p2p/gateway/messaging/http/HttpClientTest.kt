package net.corda.p2p.gateway.messaging.http

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.ChannelPipeline
import io.netty.channel.EventLoop
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.ssl.SslHandler
import io.netty.util.concurrent.ScheduledFuture
import net.corda.p2p.gateway.messaging.ConnectionConfiguration
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.SslConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.net.URI
import java.security.KeyStore
import java.security.cert.PKIXBuilderParameters
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManagerFactory

class HttpClientTest {
    private val trustStore = mock<KeyStore>()
    private val destinationInfo = DestinationInfo(
        uri = URI("http://www.r3.com:3023"),
        sni = "sni",
        legalName = null,
        trustStore,
        null,
    )
    private val sslConfiguration = mock<SslConfiguration> {
        on { revocationCheck } doReturn RevocationConfig(RevocationConfigMode.OFF)
    }
    private val loop = mock<EventLoop> {
        on { execute(any<Runnable>()) } doAnswer {
            (it.arguments.first() as Runnable).run()
        }
    }
    private val writeGroup = mock<EventLoopGroup> {
        on { next() } doReturn loop
    }
    private val nettyGroup = mock<EventLoopGroup>()
    private val listener = mock<HttpConnectionListener>()
    private val channel = mock<Channel>()
    private val pkixParams = mockConstruction(PKIXBuilderParameters::class.java)
    private val trustManagerFactory = mockStatic(TrustManagerFactory::class.java).also {
        val factory = mock<TrustManagerFactory> {
            on { trustManagers } doReturn emptyArray()
        }
        it.`when`<TrustManagerFactory> {
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        }.doReturn(factory)
    }
    private val bootstrapHandler = argumentCaptor<ChannelInitializer<SocketChannel>>()
    private val connectFuture = mock<ChannelFuture>()
    private val bootstrap = mockConstruction(Bootstrap::class.java) { mock, _ ->
        whenever(mock.group(nettyGroup)).doReturn(mock)
        whenever(mock.option(eq(ChannelOption.CONNECT_TIMEOUT_MILLIS), any())).doReturn(mock)
        whenever(mock.channel(NioSocketChannel::class.java)).doReturn(mock)
        whenever(mock.resolver(any<RandomSelectionAddressResolver>())).doReturn(mock)
        whenever(mock.handler(bootstrapHandler.capture())).doReturn(mock)
        whenever(mock.connect(any<String>(), any())).doReturn(connectFuture)
    }

    @AfterEach
    fun cleanUp() {
        pkixParams.close()
        trustManagerFactory.close()
        bootstrap.close()
    }

    private val config = ConnectionConfiguration()
    private val client = HttpClient(
        destinationInfo, sslConfiguration, writeGroup, nettyGroup, config, listener
    )

    @Test
    fun `start gets the next write group and tries to connect`() {
        client.start()

        verify(writeGroup).next()
        verify(bootstrap.constructed().first()).group(nettyGroup)
        verify(bootstrap.constructed().first()).option(
            ChannelOption.CONNECT_TIMEOUT_MILLIS,
            config.acquireTimeout.toMillis().toInt()
        )
        verify(bootstrap.constructed().first()).channel(NioSocketChannel::class.java)
        verify(bootstrap.constructed().first()).connect("www.r3.com", 3023)
    }

    @Test
    fun `start adds listener that will close if failed`() {
        val connectListener = argumentCaptor<ChannelFutureListener>()
        whenever(connectFuture.addListener(connectListener.capture())).doReturn(connectFuture)
        client.start()
        val future = mock<ChannelFuture> {
            on { isSuccess } doReturn false
            on { cause() } doReturn IOException("oops")
            on { channel() } doReturn channel
        }

        connectListener.firstValue.operationComplete(future)

        verify(listener).onClose(HttpConnectionEvent(channel))
    }

    @Test
    fun `start adds listener that will not close if not failed`() {
        val connectListener = argumentCaptor<ChannelFutureListener>()
        whenever(connectFuture.addListener(connectListener.capture())).doReturn(connectFuture)
        client.start()
        val future = mock<ChannelFuture> {
            on { isSuccess } doReturn true
        }

        connectListener.firstValue.operationComplete(future)

        verify(listener, times(0)).onClose(any())
    }

    @Test
    fun `start get the next write group only once`() {
        client.start()
        client.start()
        client.start()

        verify(writeGroup, times(1)).next()
    }

    @Test
    fun `start after stop will call the next write group again`() {
        client.start()
        client.close()
        client.start()

        verify(writeGroup, times(2)).next()
    }

    @Test
    fun `close will stop the client channel`() {
        client.start()
        client.onOpen(HttpConnectionEvent(channel))
        client.close()

        verify(channel).close()
    }

    @Test
    fun `close will wait for channel to be closed`() {
        val sync = mock<ChannelFuture>()
        doReturn(sync).whenever(channel).close()
        client.start()
        client.onOpen(HttpConnectionEvent(channel))
        client.close()

        verify(sync).sync()
    }

    @Test
    fun `close will cancel any retry future`() {
        val future = mock<ScheduledFuture<*>>()
        whenever(loop.schedule(any(), any(), any())).doReturn(future)
        client.start()
        client.onClose(HttpConnectionEvent(channel))

        client.close()

        verify(future).cancel(true)
    }

    @Test
    fun `write will write the correct data and populate future when response is received`() {
        val request = argumentCaptor<DefaultFullHttpRequest>()
        whenever(channel.writeAndFlush(request.capture())).doReturn(mock())
        client.start()
        client.onOpen(HttpConnectionEvent(channel))

        val future = client.write(byteArrayOf(1, 5))

        assertSoftly {
            it.assertThat(request.firstValue.uri()).isEqualTo("https://www.r3.com:3023")
            it.assertThat(request.firstValue.method()).isEqualTo(HttpMethod.POST)
            it.assertThat(request.firstValue.content().array()).isEqualTo(byteArrayOf(1, 5))
        }

        val response = mock<HttpResponse>()
        client.onResponse(response)

        assertThat(future.get()).isEqualTo(response)
    }

    @Test
    fun `sent requests with no responses will fail if connection is closed`() {
        val request = argumentCaptor<DefaultFullHttpRequest>()
        whenever(channel.writeAndFlush(request.capture())).doReturn(mock())
        client.start()
        client.onOpen(HttpConnectionEvent(channel))

        val future = client.write(byteArrayOf(1, 5))

        client.onClose(HttpConnectionEvent(channel))
        assertThatThrownBy { future.get() }
            .isInstanceOf(ExecutionException::class.java)
            .hasStackTraceContaining("Connection was closed.")
    }

    @Test
    fun `queued requests that have not been sent will fail if connection is closed`() {
        val request = argumentCaptor<DefaultFullHttpRequest>()
        whenever(channel.writeAndFlush(request.capture())).doReturn(mock())
        client.start()

        val future = client.write(byteArrayOf(1, 5))

        client.onClose(HttpConnectionEvent(channel))
        assertThatThrownBy { future.get() }
            .isInstanceOf(ExecutionException::class.java)
            .hasStackTraceContaining("Connection was closed.")
    }

    @Test
    fun `write will add requests to the queue and will write them once the connection is established`() {
        client.start()

        client.write(byteArrayOf(1))
        client.write(byteArrayOf(2))
        client.write(byteArrayOf(3))

        verify(channel, times(0)).writeAndFlush(any())
        client.onOpen(HttpConnectionEvent(channel))

        verify(channel, times(3)).writeAndFlush(any())
    }

    @Test
    fun `onOpen will propagate the event`() {
        client.onOpen(HttpConnectionEvent(channel))

        verify(listener).onOpen(HttpConnectionEvent(channel))
    }

    @Test
    fun `onClose will propagate the event`() {
        client.onClose(HttpConnectionEvent(channel))

        verify(listener).onClose(HttpConnectionEvent(channel))
    }

    @Test
    fun `onClose will try to reconnect if the client was not stopped`() {
        whenever(loop.schedule(any(), any(), any())).doAnswer {
            val runnable = it.arguments[0] as Runnable
            runnable.run()
            mock()
        }
        client.start()
        client.onClose(HttpConnectionEvent(channel))

        assertThat(bootstrap.constructed()).hasSize(2)
        bootstrap.constructed().forEach {
            verify(it).connect("www.r3.com", 3023)
        }
    }

    @Test
    fun `onClose will not try to reconnect if the client was stopped`() {
        whenever(loop.schedule(any(), any(), any())).doAnswer {
            val runnable = it.arguments[0] as Runnable
            runnable.run()
            mock()
        }
        client.start()
        client.close()
        client.onClose(HttpConnectionEvent(channel))

        assertThat(bootstrap.constructed()).hasSize(1)
        verify(bootstrap.constructed().first()).connect("www.r3.com", 3023)
    }

    @Test
    fun `onClose will cancel previous reconnection requests`() {
        val future = mock<ScheduledFuture<*>>()
        whenever(loop.schedule(any(), any(), any())).doReturn(future)
        client.start()
        client.onClose(HttpConnectionEvent(channel))

        client.onClose(HttpConnectionEvent(channel))
        client.onClose(HttpConnectionEvent(channel))

        verify(future, times(2)).cancel(true)
    }

    @Test
    fun `onClose will double the delay between reconnections`() {
        val future = mock<ScheduledFuture<*>>()
        whenever(loop.schedule(any(), any(), any())).doReturn(future)
        client.start()
        client.onClose(HttpConnectionEvent(channel))

        client.onClose(HttpConnectionEvent(channel))
        client.onClose(HttpConnectionEvent(channel))

        verify(loop).schedule(any(), eq(1000), eq(TimeUnit.MILLISECONDS))
        verify(loop).schedule(any(), eq(2000), eq(TimeUnit.MILLISECONDS))
        verify(loop).schedule(any(), eq(4000), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `onClose will stop doubling after reaching the maxmimal value`() {
        val future = mock<ScheduledFuture<*>>()
        whenever(loop.schedule(any(), any(), any())).doReturn(future)
        client.start()

        repeat(10) {
            client.onClose(HttpConnectionEvent(channel))
        }

        // 7 times -> 10 - 3 ( = 1 second, 2 second, 4 seconds)
        verify(loop, times(7)).schedule(any(), eq(8000), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `initChannel in ClientChannelInitializer add handlers to pipeline`() {
        client.start()
        client.onOpen(HttpConnectionEvent(channel))
        val pipeline = mock<ChannelPipeline>()
        val channel = mock<SocketChannel> {
            on { pipeline() } doReturn pipeline
        }
        val context = mock<ChannelHandlerContext> {
            on { channel() } doReturn channel
            on { pipeline() } doReturn pipeline
            on { executor() } doReturn mock()
        }

        bootstrapHandler.firstValue.channelRegistered(context)

        verify(pipeline).addLast(eq("sslHandler"), any<SslHandler>())
        verify(pipeline).addLast(any<HttpClientCodec>())
        verify(pipeline).addLast(any<HttpClientChannelHandler>())
    }
}
