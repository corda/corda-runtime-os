package net.corda.p2p.gateway.messaging.http

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.timeout.IdleStateHandler
import net.corda.lifecycle.Lifecycle
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.v5.base.util.contextLogger
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.net.BindException
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
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
 * @param host [String] value representing a host name or IP address used when binding the server
 * @param port port number used when binding the server
 * @param sslConfig the configuration to be used for the one-way TLS handshake
 */
class HttpServer(
    private val host: String,
    private val port: Int,
    private val sslConfig: SslConfiguration
) : Lifecycle, HttpEventListener {

    companion object {
        private val logger = contextLogger()

        /**
         * Default number of thread to use for the worker group
         */
        private const val NUM_SERVER_THREADS = 4

        /**
         * The channel will be closed if neither read nor write was performed for the specified period of time.
         */
        private const val SERVER_IDLE_TIME_SECONDS = 5
    }

    private val lock = ReentrantLock()

    private fun startServer(): AutoCloseable {
        logger.info("Starting HTTP Server")
        val bossGroup = NioEventLoopGroup(1)
        val workerGroup = NioEventLoopGroup(NUM_SERVER_THREADS)

        val server = ServerBootstrap()
        server.group(bossGroup, workerGroup).channel(NioServerSocketChannel::class.java)
            .childHandler(ServerChannelInitializer(this))
        logger.info("Trying to bind to $host:$port")
        val channelFuture = server.bind(host, port).sync()
        logger.info("Listening on port $port")
        val serverChannel = channelFuture.channel()

        serverChannel.closeFuture().addListener {
            println("QQQ on close!")
        }

        return AutoCloseable {
            println("QQQ! 3")
            serverChannel.close()
            println("QQQ! 4")

            workerGroup.shutdownGracefully()
            workerGroup.terminationFuture().sync()

            bossGroup.shutdownGracefully()
            bossGroup.terminationFuture().sync()
        }
    }

    private var serverChannel: AutoCloseable? = null
    private val clientChannels = ConcurrentHashMap<SocketAddress, Channel>()

    override val isRunning: Boolean
        get() = (serverChannel != null)

    private val eventListeners = CopyOnWriteArrayList<HttpEventListener>()

    /**
     * @throws BindException if the server cannot bind to the address provided in the constructor
     */
    override fun start() {
        lock.withLock {
            serverChannel?.close()
            serverChannel = startServer()
        }
    }

    override fun stop() {
        lock.withLock {
            try {
                logger.info("Stopping HTTP server")
                serverChannel?.close()
            } finally {
                serverChannel = null
                logger.info("HTTP server stopped")
            }
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

    override fun onOpen(event: HttpConnectionEvent) {
        clientChannels[event.channel.remoteAddress()] = event.channel
        eventListeners.forEach { it.onOpen(event) }
    }

    override fun onClose(event: HttpConnectionEvent) {
        clientChannels.remove(event.channel.remoteAddress())
        eventListeners.forEach { it.onClose(event) }
    }

    override fun onMessage(message: HttpMessage) {
        eventListeners.forEach { it.onMessage(message) }
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
            pipeline.addLast("sslHandler", createServerSslHandler(parent.sslConfig.keyStore, keyManagerFactory))
            pipeline.addLast("idleStateHandler", IdleStateHandler(0, 0, SERVER_IDLE_TIME_SECONDS))
            pipeline.addLast(HttpServerCodec())
            pipeline.addLast(HttpChannelHandler(parent, logger))
        }
    }
}
