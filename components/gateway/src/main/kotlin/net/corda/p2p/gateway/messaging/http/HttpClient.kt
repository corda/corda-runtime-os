package net.corda.p2p.gateway.messaging.http

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoop
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.util.concurrent.ScheduledFuture
import net.corda.lifecycle.Resource
import net.corda.p2p.gateway.certificates.RevocationChecker.Companion.getCertCheckingParameters
import net.corda.p2p.gateway.messaging.ConnectionConfiguration
import net.corda.p2p.gateway.messaging.SslConfiguration
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.KeyStore
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.CertPathTrustManagerParameters
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.withLock

/**
 * The [HttpClient] sends serialised application messages via POST requests to a given URI. It automatically initiates
 * HTTP(s) connection when [start] is invoked. Until connection is established, messages are queued in-memory.
 * If the connection is terminated for some reason (e.g. closed by the other side), the client initiates a new connection automatically.
 * The only exception to that is when the client is stopped (via [close]) explicitly.
 *
 * [HttpClient] allows to send multiple HTTP requests without waiting for a response.
 * Responses are matched with requests according to the order, as they arrive.
 * Clients of this class can make use of the returned futures to wait on the responses, when needed.
 *
 * [HttpClient] uses shared thread pool for Netty callbacks and another one for message queuing.
 *
 * @param destinationInfo the [DestinationInfo] object containing the destination's URI, SNI, and legal name
 * @param sslConfiguration the configuration to be used for the one-way TLS handshake
 * @param writeGroup event loop group (thread pool) for processing message writes and reconnects
 * @param nettyGroup event loop group (thread pool) for processing netty callbacks
 * @param connectionConfiguration the connection configuration
 * @param listener an (optional) listener that can be used to be informed when connection is established/closed.
 */
@Suppress("LongParameterList")
internal class HttpClient(
    private val destinationInfo: DestinationInfo,
    private val sslConfiguration: SslConfiguration,
    private val writeGroup: EventLoopGroup,
    private val nettyGroup: EventLoopGroup,
    private val connectionConfiguration: ConnectionConfiguration,
    private val listener: HttpConnectionListener? = null,
) : Resource, HttpClientListener {

    companion object {
        private val logger = LoggerFactory.getLogger(HttpClient::class.java)
    }

    private val lock = ReentrantLock()

    private val channelLock = ReentrantLock(true)

    /**
     * Queue containing messages that will be sent once the connection is established.
     * All queue operations must be synchronized through [writeProcessor].
     */
    private val requestQueue = LinkedList<Pair<HttpRequestPayload, CompletableFuture<HttpResponse>>>()

    /**
     * A list of futures for the expected responses, in the order they are expected.
     */
    private val pendingResponses = LinkedList<CompletableFuture<HttpResponse>>()

    @Volatile
    private var writeProcessor: EventLoop? = null

    @Volatile
    private var clientChannel: Channel? = null

    private val isRunning: Boolean
        get() = (writeProcessor != null)

    @Volatile
    private var explicitlyClosed: Boolean = false

    @Volatile
    private var retryDelay = connectionConfiguration.initialReconnectionDelay

    @Volatile
    private var retryFuture: ScheduledFuture<*>? = null

    private val connectListener = ChannelFutureListener { future ->
        if (!future.isSuccess) {
            logger.warn("Failed to connect to ${destinationInfo.uri}: ${future.cause().message}", future.cause())
            onClose(HttpConnectionEvent(future.channel()))
        } else {
            logger.info("Connected to ${destinationInfo.uri}")
        }
    }

    fun start() {
        lock.withLock {
            if (isRunning) {
                logger.info("HTTP client to ${destinationInfo.uri} already started")
                return
            }
            explicitlyClosed = false
            writeProcessor = writeGroup.next()
            connect()
        }
    }

    override fun close() {
        lock.withLock {
            logger.info("Stopping HTTP client to ${destinationInfo.uri}")
            retryFuture?.cancel(true)
            retryFuture = null
            explicitlyClosed = true
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
    fun write(message: HttpRequestPayload): CompletableFuture<HttpResponse> {
        val future = CompletableFuture<HttpResponse>()
        writeProcessor?.execute {
            val channel = clientChannel

            if (channel == null) {
                // Queuing messages to be sent once connection is established.
                requestQueue.offer(message to future)
            } else {
                val request = HttpHelper.createRequest(message, destinationInfo.uri)
                channelLock.withLock {
                    channel.writeAndFlush(request)
                    pendingResponses.addLast(future)
                }
                logger.debug("Sent HTTP request $request")
            }
        }

        return future
    }

    private fun connect() {
        if (!isRunning) {
            return
        }
        logger.info("Connecting to ${destinationInfo.uri}")
        val bootstrap = Bootstrap()
        bootstrap.group(nettyGroup)
            .option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                connectionConfiguration.acquireTimeout.toMillis().toInt()
            )
            .channel(NioSocketChannel::class.java)
            // using a name resolver that selects randomly an IP to ensure load will be distributed across the recipient
            //   gateways, even though the sending gateways don't share state.
            .resolver(RandomSelectionAddressResolver())
            .handler(ClientChannelInitializer())
        val clientFuture = bootstrap.connect(destinationInfo.uri.host, destinationInfo.uri.port)
        clientFuture.addListener(connectListener)
    }

    override fun onOpen(event: HttpConnectionEvent) {
        writeProcessor?.execute {
            clientChannel = event.channel
            // Send all queued messages.
            while (requestQueue.isNotEmpty()) {
                val (message, future) = requestQueue.removeFirst()
                val request = HttpHelper.createRequest(message, destinationInfo.uri)
                channelLock.withLock {
                    clientChannel!!.writeAndFlush(request)
                    pendingResponses.add(future)
                }
                logger.debug("Sent HTTP request $request")
            }

            retryDelay = connectionConfiguration.initialReconnectionDelay
        }
        listener?.onOpen(event)
    }

    override fun onClose(event: HttpConnectionEvent) {
        writeProcessor?.execute {
            clientChannel = null

            // Fail futures for pending requests that are never going to complete and queued requests, as connection was disrupted.
            channelLock.withLock {
                while (pendingResponses.isNotEmpty()) {
                    pendingResponses.removeFirst().completeExceptionally(RuntimeException("Connection was closed."))
                }
            }
            while (requestQueue.isNotEmpty()) {
                requestQueue.removeFirst().second.completeExceptionally(RuntimeException("Connection was closed."))
            }

            // If the connection wasn't explicitly closed on our side, we try to reconnect.
            if (!explicitlyClosed) {
                logger.info(
                    "Previous connection to ${destinationInfo.uri} was closed, " +
                        "a new attempt will be made to connect again in ${retryDelay.seconds} seconds."
                )
                retryFuture?.cancel(true)

                retryFuture = writeProcessor?.schedule({
                    connect()
                }, retryDelay.toMillis(), TimeUnit.MILLISECONDS)

                (retryDelay + retryDelay).also { newDelay ->
                    if (newDelay <= connectionConfiguration.maxReconnectionDelay) {
                        retryDelay = newDelay
                    }
                }
            }
        }
        listener?.onClose(event)
    }

    override fun onResponse(httpResponse: HttpResponse) {
        val future = channelLock.withLock {
            pendingResponses.removeFirst()
        }
        future.complete(httpResponse)
    }

    private inner class ClientChannelInitializer : ChannelInitializer<SocketChannel>() {
        private val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

        init {
            sslConfiguration.run {
                val pkixParams = getCertCheckingParameters(destinationInfo.trustStore, revocationCheck)
                trustManagerFactory.init(CertPathTrustManagerParameters(pkixParams))
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
                    trustManagerFactory,
                    destinationInfo.clientCertificatesKeyStore,
                )
            )
            pipeline.addLast(HttpClientCodec())
            pipeline.addLast(HttpClientChannelHandler(this@HttpClient, logger))
        }
    }
}

/**
 * @param uri the destination URI
 * @param sni the destination server name
 * @param legalName the destination legal name expected to be on the TLS certificate. If the value is *null*, the [HttpClient]
 * @param trustStore Key store containing the certificates trusted for this specific destination.
 * @param clientCertificatesKeyStore The client certificates key store to be used for mutual TLS mode (null for one way TLS).
 * will use standard target identity check
 */
internal data class DestinationInfo(
    val uri: URI,
    val sni: String,
    val legalName: X500Name?,
    val trustStore: KeyStore,
    val clientCertificatesKeyStore: KeyStoreWithPassword?
)

typealias HttpRequestPayload = ByteArray
