package net.corda.p2p.gateway.messaging.http

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.lifecycle.LifeCycle
import net.corda.p2p.gateway.messaging.SslConfiguration
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.net.BindException
import java.net.SocketAddress
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.KeyManagerFactory
import kotlin.concurrent.withLock

/**
 * The [HttpServer] is responsible for opening a socket listener on the configured port in order to receive and handle
 * multiple HTTP(S) connections.
 *
 * The server hosts only one endpoint which can be used for POST requests. In the future, it may host versioned endpoints.
 *
 * The body of the POST requests should contain serialised (and possibly encrypted) Corda P2P messages. The server is not
 * responsible for validating these messages, only the request headers. Once a request is checks out, its body is sent upstream
 * and a response is sent back to the client. The response body is empty unless it follows a session handshake request,
 * in which case the body will contain additional information.
 *
 * The server provides two observables [onReceive] and [onConnection] which upstream services can subscribe to receive
 * updates on important events.
 */
class HttpServer(private val host: String, private val port: Int, private val sslConfig: SslConfiguration)
    : HttpEventHandler, LifeCycle {

    companion object {
        private val logger = LoggerFactory.getLogger(HttpServer::class.java)

        /**
         * Default number of thread to use for the worker group
         */
        private const val NUM_SERVER_THREADS = 4
    }

    private val lock = ReentrantLock()
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var serverChannel: Channel? = null
    private val clientChannels = ConcurrentHashMap<SocketAddress, SocketChannel>()

    private val onConnectionCallbacks = LinkedList<(ConnectionChangeEvent) -> Unit>()
    private val onMessageCallbacks = LinkedList<(HttpMessage) -> Unit>()

    private var started = false
    override val isRunning: Boolean
        get() = started

    /**
     * @throws BindException if the server cannot bind to the address provided in the constructor
     */
    override fun start() {
        lock.withLock {
            logger.info("Starting HTTP Server")
            bossGroup = NioEventLoopGroup(1)
            workerGroup = NioEventLoopGroup(NUM_SERVER_THREADS)

            val server = ServerBootstrap()
            server.group(bossGroup, workerGroup).channel(NioServerSocketChannel::class.java)
//                .handler(LoggingHandler(LogLevel.INFO))
                .childHandler(ServerChannelInitializer(this))
            logger.info("Trying to bind to $host:$port")
            val channelFuture = server.bind(host, port).sync()
            logger.info("Listening on port $port")
            serverChannel = channelFuture.channel()
            started = true
        }
    }

    override fun stop() {
        lock.withLock {
            try {
                logger.info("Stopping HTTP server")
                serverChannel?.apply { close() }
                serverChannel = null

                workerGroup?.shutdownGracefully()
                workerGroup?.terminationFuture()?.sync()

                bossGroup?.shutdownGracefully()
                bossGroup?.terminationFuture()?.sync()

                workerGroup = null
                bossGroup = null
            } finally {
                started = false
                logger.info("HTTP server stopped")
            }
        }
    }

    override fun registerConnectionHandler(handler: (ConnectionChangeEvent) -> Unit) {
        onConnectionCallbacks.add(handler)
    }

    override fun registerMessageHandler(handler: (HttpMessage) -> Unit) {
        onMessageCallbacks.add(handler)
    }

    override fun unregisterConnectionHandlers() = onConnectionCallbacks.clear()
    override fun unregisterMessageHandlers() = onMessageCallbacks.clear()

    /**
     * Writes the given message to the channel corresponding to the recipient address. This method should be called
     * by upstream services in order to send an HTTP response
     * @param message
     * @param destination
     * @throws IllegalStateException if the connection to the peer is not active. This can happen because either the peer
     * has closed it or the server is stopped
     */
    @Throws(IllegalStateException::class)
    fun write(statusCode: HttpResponseStatus, message: ByteArray, destination: SocketAddress) {
        val channel = clientChannels[destination]
        if (channel == null) {
            throw IllegalStateException("Connection to $destination not active")
        } else {
            logger.debug("Writing HTTP response to channel $channel")
            val response = HttpHelper.createResponse(message, statusCode)
            channel.writeAndFlush(response)
            logger.debug("Done writing HTTP response to channel $channel")
        }
    }

    private class ServerChannelInitializer(private val parent: HttpServer) : ChannelInitializer<SocketChannel>() {

        private val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())

        init {
            parent.sslConfig.run {
                keyManagerFactory.init(this.keyStore, this.keyStorePassword.toCharArray())
            }
        }

        override fun initChannel(ch: SocketChannel) {
            val pipeline = ch.pipeline()
            pipeline.addLast("sslHandler", createServerSslHandler(keyManagerFactory))
            pipeline.addLast(HttpRequestDecoder())
            pipeline.addLast(HttpResponseEncoder())
            pipeline.addLast(HttpChannelHandler(
                onOpen = { channel, change ->
                    parent.run {
                        clientChannels[channel.remoteAddress()] = channel
                        onConnectionCallbacks.forEach { it.invoke(change) }
                    }
                },
                onClose = { channel, change ->
                    parent.run {
                        clientChannels.remove(channel.remoteAddress())
                        onConnectionCallbacks.forEach { it.invoke(change) }
                    }
                },
                onReceive = { msg ->
                    parent.onMessageCallbacks.forEach { it.invoke(msg) }
                }
            ))
        }
    }
}