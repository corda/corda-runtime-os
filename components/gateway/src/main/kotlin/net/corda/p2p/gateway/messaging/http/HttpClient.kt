package net.corda.p2p.gateway.messaging.http

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.HttpClientCodec
import net.corda.lifecycle.LifeCycle
import net.corda.p2p.gateway.messaging.SslConfiguration
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.net.URI
import java.security.cert.PKIXBuilderParameters
import java.security.cert.X509CertSelector
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.CertPathTrustManagerParameters
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.withLock

/**
 * The [HttpClient] creates an HTTP(S) connection to a given URI. It tries to initiate a connection at most once.
 * It can use a shared thread pool injected through the constructor or default to use its own Netty thread pool.
 * Once connected, it can accept serialised application messages to send via POST requests. Request responses are expected
 * to arrive shortly and they will terminate at this layer (unless a specific session handshake message is present in the
 * response body)
 * The client will never close a connection. It is the responsibility of the upstream services to decide when to close a connection.
 *
 * @param destination the target URI
 * @param sslConfiguration the configuration to be used for the one-way TLS handshake
 * @param sharedThreadPool optional thread pool
 */
class HttpClient(private val destination: URI,
                 private val sslConfiguration: SslConfiguration,
                 private val sharedThreadPool: EventLoopGroup? = null) : LifeCycle, HttpEventListener {

    companion object {
        private val logger = LoggerFactory.getLogger(HttpClient::class.java)
        private const val NUM_CLIENT_THREADS = 2
    }

    private val lock = ReentrantLock()

    @Volatile
    private var started: Boolean = false
    private var workerGroup: EventLoopGroup? = null

    @Volatile
    private var clientChannel: Channel? = null

    override val isRunning: Boolean
        get() = started

    private val eventListeners = CopyOnWriteArrayList<HttpEventListener>()

    private val connectListener = ChannelFutureListener { future ->
        if (!future.isSuccess) {
            logger.warn("Failed to connect. ${future.cause().message}")
            stop()
        } else {
            clientChannel = future.channel()
            clientChannel?.closeFuture()?.addListener(closeListener)
            logger.info("Connected to ${destination.authority}")
        }
    }

    private val closeListener = ChannelFutureListener { future ->
        logger.info("Disconnected from $destination")
        future.channel()?.disconnect()
        clientChannel = null
    }

    override fun start() {
        lock.withLock {
            if (started) {
                logger.info("Already connected to ${destination.authority}")
                return
            }
            logger.info("Connecting to $destination")
            workerGroup = sharedThreadPool ?: NioEventLoopGroup(NUM_CLIENT_THREADS)
            started = true
            connect()
        }

    }

    override fun stop() {
        lock.withLock {
            logger.info("Stopping connection to ${destination.authority}")
            started = false
            if (sharedThreadPool == null) {
                workerGroup?.shutdownGracefully()
                workerGroup?.terminationFuture()?.sync()
            }

            clientChannel?.close()
            clientChannel = null
            workerGroup = null
            logger.info("Stopped connection to ${destination.authority}")
        }
    }

    /**
     * Adds an [HttpEventListener] which upstream services can provide to receive updates on important events.
     */
    fun addListener(eventListener: HttpEventListener) {
        eventListeners.add(eventListener)
    }

    fun removeListener(eventListener: HttpEventListener) {
        eventListeners.remove(eventListener)
    }

    /**
     * Creates and sends a POST request. The body content type is JSON and will contain the [message].
     * @param message the bytes payload to be sent
     * @throws IllegalStateException if the connection is down
     */
    fun send(message: ByteArray) {
        val channel = clientChannel
        if (channel == null || !isChannelWritable(channel)) {
            throw IllegalStateException("Connection to ${destination.authority} not active")
        } else {
            val request = HttpHelper.createRequest(message, destination)
            channel.writeAndFlush(request)
            logger.debug("Sent HTTP request $request")
        }
    }

    val connected: Boolean
        get() {
            val channel = lock.withLock { clientChannel }
            return isChannelWritable(channel)
        }

    private fun connect() {
        val bootstrap = Bootstrap()
        bootstrap.group(workerGroup).channel(NioSocketChannel::class.java).handler(ClientChannelInitializer(this))
        val clientFuture = bootstrap.connect(destination.host, destination.port)
        clientFuture.addListener(connectListener)
    }

    private fun isChannelWritable(channel: Channel?): Boolean {
        return channel?.let { channel.isOpen && channel.isActive } ?: false
    }

    override fun onOpen(event: HttpConnectionEvent) {
        eventListeners.forEach { it.onOpen(event) }
    }

    override fun onClose(event: HttpConnectionEvent) {
        eventListeners.forEach { it.onClose(event) }
    }

    override fun onMessage(message: HttpMessage) {
        eventListeners.forEach { it.onMessage(message) }
    }

    private class ClientChannelInitializer(val parent: HttpClient) : ChannelInitializer<SocketChannel>() {
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
            pipeline.addLast(HttpChannelHandler(parent, logger))
        }
    }
}