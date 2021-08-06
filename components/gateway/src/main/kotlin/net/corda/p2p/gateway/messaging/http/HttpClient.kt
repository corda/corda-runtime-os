package net.corda.p2p.gateway.messaging.http

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoop
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.timeout.IdleStateHandler
import net.corda.lifecycle.Lifecycle
import net.corda.p2p.gateway.messaging.SslConfiguration
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.net.URI
import java.security.cert.PKIXBuilderParameters
import java.security.cert.X509CertSelector
import java.util.LinkedList
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.CertPathTrustManagerParameters
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.withLock

/**
 * The [HttpClient] sends serialised application messages via POST requests to a given URI. It automatically initiates
 * HTTP(s) connection is on the first message. This connection can be also reused to deliver the subsequent messages,
 * however it's closed after certain period of inactivity, when a new connection will be established on demand.
 * [HttpClient] can have at most one connection at a given moment of time.
 *
 * [HttpClient] allows to send multiple HTTP requests without waiting a response. Every request is queued until getting
 * the response. Responses are matched with requests according to the order, as they arrive. Failed requests (including
 * connection and send failures, as well as missing response) are resent multiple times as defined by message TTL setting.
 *
 * [HttpClient] uses shared thread pool for Netty callbacks and another one for message queuing.
 *
 * @param destination the target URI
 * @param sslConfiguration the configuration to be used for the one-way TLS handshake
 * @param writeGroup event loop group (thread pool) for processing message writes and reconnects
 * @param nettyGroup event loop group (thread pool) for processing netty callbacks
 */
class HttpClient(private val destination: URI,
                 private val sslConfiguration: SslConfiguration,
                 private val writeGroup: EventLoopGroup,
                 private val nettyGroup: EventLoopGroup) : Lifecycle, HttpEventListener {

    companion object {
        private val logger = LoggerFactory.getLogger(HttpClient::class.java)

        /**
         * Number of attempts to send the message before giving up on failure.
         */
        private const val MESSAGE_TTL = 3

        /**
         * The channel will be closed if neither read nor write was performed for the specified period of time.
         */
        private const val CLIENT_IDLE_TIME_SECONDS = 5
    }

    private val lock = ReentrantLock()

    private class Message(val payload: ByteArray, var ttl: Int)

    // All queue operations must be synchronized through writeProcessor.
    private val requestQueue = LinkedList<Message>()

    @Volatile
    private var writeProcessor: EventLoop? = null

    @Volatile
    private var clientChannel: Channel? = null

    override val isRunning: Boolean
        get() = (writeProcessor != null)

    private val eventListeners = CopyOnWriteArrayList<HttpEventListener>()

    private val connectListener = ChannelFutureListener { future ->
        if (!future.isSuccess) {
            logger.warn("Failed to connect to $destination: ${future.cause().message}")
            onClose(HttpConnectionEvent(future.channel()))
        } else {
            logger.info("Connected to $destination")
        }
    }

    override fun start() {
        lock.withLock {
            if (isRunning) {
                logger.info("HTTP client $destination already started")
                return
            }
            writeProcessor = writeGroup.next()
        }

    }

    override fun stop() {
        lock.withLock {
            logger.info("Stopping HTTP client $destination")
            clientChannel?.close()?.sync()
            writeProcessor = null
            logger.info("Stopped HTTP client $destination")
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
    fun write(message: ByteArray) {
        write(Message(message, ttl = MESSAGE_TTL), addToQueue = true)
    }

    private fun write(message: Message, addToQueue: Boolean) {
        writeProcessor?.execute {
            val channel = clientChannel
            // Connect on demand on the first message. Message itself will be queued and sent later.
            if (channel == null && requestQueue.isEmpty()) {
                connect()
            }
            if (channel != null) {
                val request = HttpHelper.createRequest(message.payload, destination)
                channel.writeAndFlush(request)
                logger.debug("Sent HTTP request $request")
            }
            if (addToQueue) {
                requestQueue.offer(message)
            }
        }
    }

    private fun connect() {
        if (!isRunning) {
            return
        }
        logger.info("Connecting to $destination")
        val bootstrap = Bootstrap()
        bootstrap.group(nettyGroup).channel(NioSocketChannel::class.java).handler(ClientChannelInitializer(this))
        val clientFuture = bootstrap.connect(destination.host, destination.port)
        clientFuture.addListener(connectListener)
    }

    override fun onOpen(event: HttpConnectionEvent) {
        writeProcessor?.execute {
            clientChannel = event.channel
            // Resend all undelivered messages.
            requestQueue.forEach { write(it, addToQueue = false) }

        }
        eventListeners.forEach { it.onOpen(event) }
    }

    override fun onClose(event: HttpConnectionEvent) {
        writeProcessor?.execute {
            clientChannel = null
            // Reduce TTL of undelivered messages: they will be resent on the next connection if TTL > 0.
            requestQueue.forEach { it.ttl-- }
            requestQueue.removeIf { it.ttl <= 0 }

            // Automatically reconnect if there are pending messages in queue.
            if (requestQueue.isNotEmpty()) {
                logger.info("${requestQueue.size} pending message(s) in queue for $destination")
                connect()
            }
        }
        eventListeners.forEach { it.onClose(event) }
    }

    override fun onMessage(message: HttpMessage) {
        writeProcessor?.execute {
            if (clientChannel != null) {
                // Remove first pending message from queue on the ack.
                requestQueue.poll()
            }
        }
        eventListeners.forEach { it.onMessage(message) }
    }

    private class ClientChannelInitializer(val parent: HttpClient) : ChannelInitializer<SocketChannel>() {
        private val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

        init {
            parent.sslConfiguration.run {
                val pkixParams = getCertCheckingParameters(trustStore, revocationCheck)
                trustManagerFactory.init(pkixParams)
            }
        }

        override fun initChannel(ch: SocketChannel) {
            val pipeline = ch.pipeline()
            pipeline.addLast("sslHandler", createClientSslHandler(parent.destination, trustManagerFactory))
            pipeline.addLast("idleStateHandler", IdleStateHandler(0, 0, CLIENT_IDLE_TIME_SECONDS))
            pipeline.addLast(HttpClientCodec())
            pipeline.addLast(HttpChannelHandler(parent, logger))
        }
    }
}