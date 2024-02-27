package net.corda.p2p.gateway.messaging.http

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.timeout.IdleStateHandler
import net.corda.lifecycle.Resource
import net.corda.p2p.gateway.messaging.GatewayServerConfiguration
import net.corda.p2p.gateway.messaging.internal.RequestListener
import org.slf4j.LoggerFactory
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.X509ExtendedTrustManager
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
 */
@Suppress("LongParameterList")
internal class HttpServer(
    private val eventListener: RequestListener,
    private val maxRequestSize: Long,
    private val serverConfiguration: GatewayServerConfiguration,
    private val keyStore: KeyStoreWithPassword,
    private val serverTrustManager: X509ExtendedTrustManager?,
    private val groupFactory: (Int) -> NioEventLoopGroup = { NioEventLoopGroup(it) },
) : Resource,
    HttpServerListener,
    HttpWriter {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        /**
         * Default number of thread to use for the worker group
         */
        private const val NUM_SERVER_THREADS = 4

        /**
         * The channel will be closed if neither read nor write was performed for the specified period of time.
         * Note: Inactive connections are normally closed by clients.
         *       Closing them eagerly on the server side too would be wasteful as clients would automatically try to reconnect.
         *       This is just a last resort to protect against misbehaving clients.
         */
        private const val SERVER_IDLE_TIME_SECONDS = 60 * 10

        /**
         * If the response code is not any of the below, then the connection should be closed eagerly.
         * If it's any of the below response codes, it can be kept open.
         */
        private val BENIGN_STATUS_CODES = setOf(
            HttpResponseStatus.OK,
            HttpResponseStatus.GONE
        )
    }

    private val clientChannels = ConcurrentHashMap<SocketAddress, Channel>()
    private val lock = ReentrantLock()
    private val shutdownSequence = ConcurrentLinkedDeque<() -> Unit>()

    /**
     * Writes the given message to the channel corresponding to the recipient address. This method should be called
     * by upstream services in order to send an HTTP response
     * @param message
     * @param destination
     * @throws IllegalStateException if the connection to the peer is not active. This can happen because either the peer
     * has closed it or the server is stopped
     */
    @Throws(IllegalStateException::class)
    override fun write(statusCode: HttpResponseStatus, destination: SocketAddress, message: ByteArray) {
        val channel = clientChannels[destination]
        if (channel == null) {
            throw IllegalStateException("Connection to $destination not active")
        } else {
            logger.debug("Writing HTTP response to channel $channel")
            val response = HttpHelper.createResponse(message, statusCode)
            if (shouldCloseConnection(statusCode)) {
                channel.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
            } else {
                channel.writeAndFlush(response)
            }
            logger.debug("Done writing HTTP response to channel $channel")
        }
    }

    override fun onOpen(event: HttpConnectionEvent) {
        clientChannels[event.channel.remoteAddress()] = event.channel
    }

    override fun onClose(event: HttpConnectionEvent) {
        clientChannels.remove(event.channel.remoteAddress())
    }

    override fun onRequest(request: HttpRequest) {
        eventListener.onRequest(this, request)
    }

    private inner class ServerChannelInitializer : ChannelInitializer<SocketChannel>() {

        override fun initChannel(ch: SocketChannel) {
            val pipeline = ch.pipeline()
            pipeline.addLast("sslHandler", createServerSslHandler(keyStore, serverTrustManager))
            pipeline.addLast("idleStateHandler", IdleStateHandler(0, 0, SERVER_IDLE_TIME_SECONDS))
            pipeline.addLast(HttpServerCodec())
            pipeline.addLast(HttpServerChannelHandler(this@HttpServer, maxRequestSize, serverConfiguration.urlPaths, logger))
        }
    }

    internal val isRunning: Boolean
        get() {
            lock.withLock {
                return shutdownSequence.isNotEmpty()
            }
        }

    override fun close() {
        lock.withLock {
            shutdownSequence.forEach {
                try {
                    it.invoke()
                } catch (e: Throwable) {
                    logger.warn("Could not stop HTTP server", e)
                }
            }
            val host = serverConfiguration.hostAddress
            val port = serverConfiguration.hostPort
            serverConfiguration.urlPaths.forEach { path ->
                logger.info("Stopping HTTP Server $host:$port$path")
            }
            shutdownSequence.clear()
        }
    }

    fun start() {
        lock.withLock {
            if (shutdownSequence.isEmpty()) {
                logger.info("Starting HTTP Server")
                val bossGroup = groupFactory(1).also {
                    shutdownSequence.addFirst {
                        it.shutdownGracefully()
                        it.terminationFuture().sync()
                    }
                }
                val workerGroup = groupFactory(NUM_SERVER_THREADS).also {
                    shutdownSequence.addFirst {
                        it.shutdownGracefully()
                        it.terminationFuture().sync()
                    }
                }

                val server = ServerBootstrap()
                server.group(bossGroup, workerGroup).channel(NioServerSocketChannel::class.java)
                    .childHandler(ServerChannelInitializer())
                val host = serverConfiguration.hostAddress
                val port = serverConfiguration.hostPort
                logger.info("Trying to bind to $host:$port")
                val channelFuture = server.bind(host, port).sync()
                logger.info("Listening on port $port")
                channelFuture.channel().also { serverChannel ->
                    shutdownSequence.addFirst {
                        if (serverChannel.isOpen) {
                            serverChannel.close().sync()
                        }
                    }
                }
                shutdownSequence.addFirst {
                    clientChannels.clear()
                }
                logger.info("HTTP Server started")
            }
        }
    }

    private fun shouldCloseConnection(responseStatus: HttpResponseStatus): Boolean {
        return responseStatus !in BENIGN_STATUS_CODES
    }
}
