package net.corda.p2p.gateway.messaging.http

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpContentDecompressor
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.LastHttpContent
import io.netty.handler.ssl.SslHandshakeCompletionEvent
import net.corda.lifecycle.LifeCycle
import net.corda.p2p.gateway.messaging.ResponseMessage
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.toHostAndPort
import net.corda.v5.base.util.NetworkHostAndPort
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.lang.IllegalStateException
import java.net.InetSocketAddress
import java.nio.channels.ClosedChannelException
import java.security.cert.PKIXBuilderParameters
import java.security.cert.X509CertSelector
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.CertPathTrustManagerParameters
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.withLock
import kotlin.math.min

/**
 * The [HttpClient] creates an HTTP(S) connection to a given URL. It will attempt to keep the connection alive until a set number
 * of retries has been reached. It can use a shared thread pool injected through the constructor or default to use its
 * own Netty thread pool.
 * Once connected, it can accept serialised application messages to send via POST requests. Request responses are expected
 * to arrive shortly and they will terminate at this layer (unless a specific session handshake message is present in the
 * response body)
 *
 * The client is responsible for keeping the connection up. On loss, it will attempt to reconnect with exponential back-off.
 * It is the responsibility of the upstream services to decide when to close a connection.
 *
 * The client provides two observables [onReceive] and [onConnection] which upstream services can subscribe to receive
 * updates on important events.
 *
 * @param destination the target address; TODO: will probably become some URL in the future like https://bankofcorda.net:6666
 */
class HttpClient(private val destination: NetworkHostAndPort,
                 private val sslConfiguration: SslConfiguration,
                 private val sharedThreadPool: EventLoopGroup? = null) :
    LifeCycle {

    companion object {
        private const val MIN_RETRY_INTERVAL = 1000L
        private const val MAX_RETRY_INTERVAL = 60000L
        private const val BACKOFF_MULTIPLIER = 2L
        /**
         * Default number of thread to use for the worker group
         */
        private const val NUM_CLIENT_THREADS = 2
    }

    private val logger = LoggerFactory.getLogger(HttpClient::class.java)

    private var retryInterval = MIN_RETRY_INTERVAL
    private val lock = ReentrantLock()

    @Volatile
    private var started: Boolean = false
    private var workerGroup: EventLoopGroup? = null

    @Volatile
    private var clientChannel: Channel? = null

    @Volatile
    private var httpActive = false

    @Volatile
    private var httpChannelHandler: ChannelHandler? = null

    override val isRunning: Boolean
        get() = started

    private val _onReceive = PublishSubject.create<ResponseMessage>().toSerialized()
    val onReceive: Observable<ResponseMessage>
        get() = _onReceive

    private val _onConnection = PublishSubject.create<ConnectionChangeEvent>().toSerialized()
    val onConnection: Observable<ConnectionChangeEvent>
        get() = _onConnection

    private val connectListener = ChannelFutureListener { future ->
        httpActive = false
        if (!future.isSuccess) {
            logger.warn("Failed to connect. ${future.cause().message}")
            if (started) {
                workerGroup?.schedule({
                    logger.info("Retry connect to $destination")
                    retryInterval = min(MAX_RETRY_INTERVAL, retryInterval * BACKOFF_MULTIPLIER)
                    restart()
                }, retryInterval, TimeUnit.MILLISECONDS)
            }
        } else {
            clientChannel = future.channel()
            clientChannel?.closeFuture()?.addListener(closeListener)
            logger.info("Connected to $destination")
        }
    }

    private val closeListener = ChannelFutureListener { future ->
        logger.info("Disconnected from $destination")
        future.channel()?.disconnect()
        clientChannel = null
        if (started && !httpActive) {
            logger.info("Scheduling reconnect to $destination reason HTTP channel inactive")
            workerGroup?.schedule({
                logger.info("Retry connect to $destination")
                retryInterval = min(MAX_RETRY_INTERVAL, retryInterval * BACKOFF_MULTIPLIER)
                restart()
            }, retryInterval, TimeUnit.MILLISECONDS)
        }
    }

    override fun start() {
        lock.withLock {
            if (started) {
                logger.info("Already connected to $destination")
                return
            }
            logger.info("Connecting to $destination")
            workerGroup = sharedThreadPool ?: NioEventLoopGroup(NUM_CLIENT_THREADS)
            started = true
            restart()
        }

    }

    override fun stop() {
        lock.withLock {
            logger.info("Stopping connection to $destination")
            started = false
            if (sharedThreadPool == null) {
                workerGroup?.shutdownGracefully()
                workerGroup?.terminationFuture()?.sync()
            }

            clientChannel?.close()
            clientChannel = null
            workerGroup = null
            logger.info("Stopped connection to $destination")
        }
    }

    /**
     * Creates and sends a POST request. The body content type is JSON and will contain the [message].
     * @param message the bytes payload to be sent
     * @throws IllegalStateException if the connection is down
     */
    fun send(message: ByteArray) {
        val channel = clientChannel
        if (channel == null || !isChannelWritable(channel)) {
            throw IllegalStateException("Connection to $destination not active")
        } else {
            val request = HttpHelper.createRequest(message, destination)
            channel.writeAndFlush(request)
            logger.info("Sent HTTP request $request")
        }
    }

    val connected: Boolean
        get() {
            val channel = lock.withLock { clientChannel }
            return isChannelWritable(channel)
        }

    private fun restart() {
        val bootstrap = Bootstrap()
        bootstrap.group(workerGroup).channel(NioSocketChannel::class.java).handler(ClientChannelInitializer(this))
        val clientFuture = bootstrap.connect(destination.host, destination.port)
        clientFuture.addListener(connectListener)
    }

    private fun isChannelWritable(channel: Channel?): Boolean {
        return channel?.let { channel.isOpen && channel.isActive && httpActive } ?: false
    }

    private class ClientChannelInitializer(val parent: HttpClient) : ChannelInitializer<SocketChannel>() {
        @Volatile
        private lateinit var httpChannelHandler: HttpClientChannelHandler
        private val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

        init {
            parent.sslConfiguration.run {
                val pkixParams = PKIXBuilderParameters(this.trustStore, X509CertSelector())
                pkixParams.addCertPathChecker(AllowAllRevocationChecker)
                trustManagerFactory.init(CertPathTrustManagerParameters(pkixParams))
            }
        }

        override fun initChannel(ch: SocketChannel) {
            val pipeline = ch.pipeline()
            pipeline.addLast("sslHandler", createClientSslHandler(parent.destination, trustManagerFactory))
            pipeline.addLast(HttpClientCodec())
            pipeline.addLast(HttpContentDecompressor())
            httpChannelHandler = HttpClientChannelHandler(
                onOpen = { _, change ->
                    parent.run {
                        httpActive = true
                        retryInterval = MIN_RETRY_INTERVAL
                        _onConnection.onNext(change)
                    }
                },
                onClose = { _, change ->
                    if (parent.httpChannelHandler == httpChannelHandler) {
                        parent.run {
                            _onConnection.onNext(change)
                            if (started && httpActive) {
                                logger.info("Scheduling restart of connection to ${parent.destination} (HTTP active)")
                                workerGroup?.schedule({
                                    retryInterval = min(MAX_RETRY_INTERVAL, retryInterval * BACKOFF_MULTIPLIER)
                                    restart()
                                }, retryInterval, TimeUnit.MILLISECONDS)
                            }
                            httpActive = false
                        }
                    }
                },
                onReceive = { rcv -> parent._onReceive.onNext(rcv) }
            )
            pipeline.addLast(httpChannelHandler)
            parent.httpChannelHandler = httpChannelHandler
        }
    }

    private class HttpClientChannelHandler(
        private val onOpen: (SocketChannel, ConnectionChangeEvent) -> Unit,
        private val onClose: (SocketChannel, ConnectionChangeEvent) -> Unit,
        private val onReceive: (ResponseMessage) -> Unit
    ) : SimpleChannelInboundHandler<HttpObject>() {

        private val logger = LoggerFactory.getLogger(HttpClientChannelHandler::class.java)
        private var responseBodyBuf: ByteBuf? = null
        private var responseCode: HttpResponseStatus? = null

        /**
         * Reads the responses into a [ByteBuf] and forwards them to an event processor
         */
        override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
            if (msg is HttpResponse) {
                logger.debug("Received response $msg")
                responseBodyBuf = ctx.alloc().buffer(msg.headers()[HttpHeaderNames.CONTENT_LENGTH].toInt())
                responseCode = msg.status()
            }

            if (msg is HttpContent) {
                logger.debug("Received response body $msg")
                val content = msg.content()
                if (content.isReadable) {
                    content.readBytes(responseBodyBuf, content.readableBytes())
                }
            }

            // This message type indicates the entire response has been received and the body content can be forwarded to
            // the event processor. No trailing headers should exist
            if (msg is LastHttpContent) {
                logger.debug("Read end of response body")
                val sourceAddress = ctx.channel().remoteAddress() as InetSocketAddress
                val targetAddress = ctx.channel().localAddress() as InetSocketAddress
                val returnByteArray = ByteArray(responseBodyBuf!!.readableBytes())
                responseBodyBuf!!.readBytes(returnByteArray)
                onReceive(ResponseMessage(responseCode!!, returnByteArray,
                    sourceAddress.toHostAndPort(), targetAddress.toHostAndPort()))
                responseBodyBuf?.release()
            }
        }

        override fun channelActive(ctx: ChannelHandlerContext) {
            val ch = ctx.channel()
            logger.info("New client connection ${ch.id()} from ${ch.localAddress()} to ${ch.remoteAddress()}")
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            val ch = ctx.channel()
            logger.info("Closed client connection ${ch.id()} from ${ch.localAddress()} to ${ch.remoteAddress()}")
            responseBodyBuf?.let {
                if (it.refCnt() > 0)
                    it.release()
            }
            val remoteAddress = (ch.remoteAddress() as InetSocketAddress).let { NetworkHostAndPort(it.hostName, it.port) }
            onClose(ch as SocketChannel, ConnectionChangeEvent(remoteAddress, false))
            ctx.fireChannelInactive()
        }

        override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
            when (evt) {
                is SslHandshakeCompletionEvent -> {
                    if (evt.isSuccess) {
                        val ch = ctx.channel()
                        val remoteAddress = (ch.remoteAddress() as InetSocketAddress).let { NetworkHostAndPort(it.hostName, it.port) }
                        logger.info("Handshake with ${ctx.channel().remoteAddress()} successful")
                        onOpen(ch as SocketChannel, ConnectionChangeEvent(remoteAddress, true))
                    } else {
                        val cause = evt.cause()
                        if (cause is ClosedChannelException) {
                            logger.warn("SSL handshake closed early")
                        } else if (cause is SSLException && cause.message == "handshake timed out") {
                            logger.warn("SSL handshake timed out")
                        }
                        logger.error("Handshake failure ${evt.cause().message}")
                        ctx.close()
                    }
                }
            }
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            logger.warn("Closing channel due to unrecoverable exception ${cause.message}")
            cause.printStackTrace()
            ctx.close()
        }
    }
}