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
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.net.URI
import java.util.LinkedList
import java.util.concurrent.locks.ReentrantLock
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
 * @param destinationInfo the [DestinationInfo] object containing the destination's URI, SNI, and legal name
 * @param sslConfiguration the configuration to be used for the one-way TLS handshake
 * @param writeGroup event loop group (thread pool) for processing message writes and reconnects
 * @param nettyGroup event loop group (thread pool) for processing netty callbacks
 */
class HttpClient(
    private val destinationInfo: DestinationInfo,
    private val sslConfiguration: SslConfiguration,
    private val writeGroup: EventLoopGroup,
    private val nettyGroup: EventLoopGroup,
    private val listener: HttpEventListener,
) : Lifecycle, HttpEventListener {

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

    private val connectListener = ChannelFutureListener { future ->
        if (!future.isSuccess) {
            logger.warn("Failed to connect to ${destinationInfo.uri}: ${future.cause().message}")
            onClose(HttpConnectionEvent(future.channel()))
        } else {
            logger.info("Connected to ${destinationInfo.uri}")
        }
    }

    override fun start() {
        lock.withLock {
            if (isRunning) {
                logger.info("HTTP client to ${destinationInfo.uri} already started")
                return
            }
            writeProcessor = writeGroup.next()
        }
    }

    override fun stop() {
        lock.withLock {
            logger.info("Stopping HTTP client to ${destinationInfo.uri}")
            clientChannel?.close()?.sync()
            writeProcessor = null
            logger.info("Stopped HTTP client ${destinationInfo.uri}")
        }
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
                val request = HttpHelper.createRequest(message.payload, destinationInfo.uri)
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
        logger.info("Connecting to ${destinationInfo.uri}")
        val bootstrap = Bootstrap()
        bootstrap.group(nettyGroup).channel(NioSocketChannel::class.java).handler(ClientChannelInitializer())
        val clientFuture = bootstrap.connect(destinationInfo.uri.host, destinationInfo.uri.port)
        clientFuture.addListener(connectListener)
    }

    override fun onOpen(event: HttpConnectionEvent) {
        writeProcessor?.execute {
            clientChannel = event.channel
            // Resend all undelivered messages.
            requestQueue.forEach { write(it, addToQueue = false) }
        }
        listener.onOpen(event)
    }

    override fun onClose(event: HttpConnectionEvent) {
        writeProcessor?.execute {
            clientChannel = null
            // Reduce TTL of undelivered messages: they will be resent on the next connection if TTL > 0.
            requestQueue.forEach { it.ttl-- }
            requestQueue.removeIf { it.ttl <= 0 }

            // Automatically reconnect if there are pending messages in queue.
            if (requestQueue.isNotEmpty()) {
                logger.info("${requestQueue.size} pending message(s) in queue for ${destinationInfo.uri}")
                connect()
            }
        }
        listener.onClose(event)
    }

    override fun onMessage(message: HttpMessage) {
        writeProcessor?.execute {
            if (clientChannel != null) {
                // Remove first pending message from queue on the ack.
                requestQueue.poll()
            }
        }
        listener.onMessage(message)
    }

    private inner class ClientChannelInitializer : ChannelInitializer<SocketChannel>() {
        private val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

        init {
            sslConfiguration.run {
                val pkixParams = getCertCheckingParameters(trustStore, revocationCheck)
                trustManagerFactory.init(pkixParams)
            }
        }

        override fun initChannel(ch: SocketChannel) {
            val pipeline = ch.pipeline()
            pipeline.addLast(
                "sslHandler",
                createClientSslHandler(
                    destinationInfo.sni,
                    destinationInfo.uri,
                    destinationInfo.legalName,
                    trustManagerFactory
                )
            )
            pipeline.addLast("idleStateHandler", IdleStateHandler(0, 0, CLIENT_IDLE_TIME_SECONDS))
            pipeline.addLast(HttpClientCodec())
            pipeline.addLast(HttpChannelHandler(this@HttpClient, logger))
        }
    }
}

/**
 * @param uri the destination URI
 * @param sni the destination server name
 * @param legalName the destination legal name expected to be on the TLS certificate. If the value is *null*, the [HttpClient]
 * will use standard target identity check
 */
data class DestinationInfo(val uri: URI, val sni: String, val legalName: X500Name?)
