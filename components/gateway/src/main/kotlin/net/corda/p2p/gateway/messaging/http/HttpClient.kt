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
import io.netty.handler.codec.http.HttpContentDecompressor
import net.corda.lifecycle.LifeCycle
import net.corda.p2p.gateway.messaging.SslConfiguration
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.net.URI
import java.security.cert.PKIXBuilderParameters
import java.security.cert.X509CertSelector
import java.util.concurrent.Executors
import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.CertPathTrustManagerParameters
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.withLock

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
 * @param destination the target URI
 */
class HttpClient(private val destination: URI,
                 private val sslConfiguration: SslConfiguration,
                 private val sharedThreadPool: EventLoopGroup? = null) :
    LifeCycle {

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

    private var connectionEventNotifier = SubmissionPublisher<ConnectionChangeEvent>(Executors.newSingleThreadExecutor(), 100)
    private var messageNotifier = SubmissionPublisher<HttpMessage>(Executors.newSingleThreadExecutor(), 100)

    override val isRunning: Boolean
        get() = started

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

            // Feels like I should close these but there are errors thrown because the channel might still
            // be active for a while after. Maybe should check if subscriptions are closed before doing offer()
//            connectionEventNotifier.close()
//            messageNotifier.close()

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

    fun registerMessageSubscriber(subscriber: Flow.Subscriber<HttpMessage>) {
        messageNotifier.subscribe(subscriber)
    }

    fun registerConnectionEventSubscriber(subscriber: Flow.Subscriber<ConnectionChangeEvent>) {
        connectionEventNotifier.subscribe(subscriber)
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

    private class ClientChannelInitializer(val parent: HttpClient) : ChannelInitializer<SocketChannel>() {
        @Volatile
        private lateinit var httpChannelHandler: HttpChannelHandler
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
            httpChannelHandler = HttpChannelHandler(
                onOpen = { _, change -> parent.connectionEventNotifier
                    .offer(change) { subscriber: Flow.Subscriber<in ConnectionChangeEvent>, event: ConnectionChangeEvent ->
                        subscriber.onError(RuntimeException("Event $event dropped"))
                        true
                    } },
                onClose = { _, change -> parent.connectionEventNotifier
                    .offer(change) { subscriber: Flow.Subscriber<in ConnectionChangeEvent>, event: ConnectionChangeEvent ->
                        subscriber.onError(RuntimeException("Event $event dropped"))
                        true
                    } },
                onReceive = { rcv ->
                    parent.messageNotifier.offer(rcv) { subscriber: Flow.Subscriber<in HttpMessage>, message: HttpMessage ->
                        subscriber.onError(java.lang.RuntimeException("Message $message dropped"))
                        true
                    }
                })
            pipeline.addLast(httpChannelHandler)
        }
    }
}