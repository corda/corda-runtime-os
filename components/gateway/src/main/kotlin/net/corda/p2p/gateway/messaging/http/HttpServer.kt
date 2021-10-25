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
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.v5.base.util.contextLogger
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
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
 */
class HttpServer(
    private val eventListener: HttpEventListener,
    private val configuration: GatewayConfiguration,
) : Lifecycle,
    HttpEventListener {

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

    private val clientChannels = ConcurrentHashMap<SocketAddress, Channel>()
    private val lock = ReentrantLock()
    private val shutdownSequence = ConcurrentLinkedDeque<()->Unit>()

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
        eventListener.onOpen(event)
    }

    override fun onClose(event: HttpConnectionEvent) {
        clientChannels.remove(event.channel.remoteAddress())
        eventListener.onClose(event)
    }

    override fun onMessage(message: HttpMessage) {
        eventListener.onMessage(message)
    }

    private inner class ServerChannelInitializer : ChannelInitializer<SocketChannel>() {

        private val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())

        init {
            configuration.sslConfig.run {
                keyManagerFactory.init(this.keyStore, this.keyStorePassword.toCharArray())
            }
        }

        override fun initChannel(ch: SocketChannel) {
            val pipeline = ch.pipeline()
            pipeline.addLast("sslHandler", createServerSslHandler(configuration.sslConfig.keyStore, keyManagerFactory))
            pipeline.addLast("idleStateHandler", IdleStateHandler(0, 0, SERVER_IDLE_TIME_SECONDS))
            pipeline.addLast(HttpServerCodec())
            pipeline.addLast(HttpChannelHandler(this@HttpServer, logger))
        }
    }

    override val isRunning: Boolean
        get() {
            lock.withLock {
                return shutdownSequence.isNotEmpty()
            }
        }

    override fun stop() {
        lock.withLock {
            shutdownSequence.forEach {
                @Suppress("TooGenericExceptionCaught")
                try {
                    it.invoke()
                } catch (e: Throwable) {
                    logger.warn("Could not stop HTTP server", e)
                }
            }

            shutdownSequence.clear()
        }
    }

    override fun start() {
        lock.withLock {
            if (shutdownSequence.isEmpty()) {
                logger.info("Starting HTTP Server")
                val bossGroup = NioEventLoopGroup(1).also {
                    shutdownSequence.addFirst {
                        it.shutdownGracefully()
                        it.terminationFuture().sync()
                    }
                }
                val workerGroup = NioEventLoopGroup(NUM_SERVER_THREADS).also {
                    shutdownSequence.addFirst {
                        it.shutdownGracefully()
                        it.terminationFuture().sync()
                    }
                }

                val server = ServerBootstrap()
                server.group(bossGroup, workerGroup).channel(NioServerSocketChannel::class.java)
                    .childHandler(ServerChannelInitializer())
                val host = configuration.hostAddress
                val port = configuration.hostPort
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
}
