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
import net.corda.lifecycle.LifecycleStatus
import net.corda.p2p.gateway.GatewayConfigurationService
import net.corda.p2p.gateway.domino.LifecycleWithCoordinator
import net.corda.p2p.gateway.domino.LifecycleWithCoordinatorAndResources
import net.corda.v5.base.util.contextLogger
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.KeyManagerFactory

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
    parent: LifecycleWithCoordinator,
    private val configurationService: GatewayConfigurationService
) : LifecycleWithCoordinatorAndResources(
    parent
),
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

    private val eventListeners = CopyOnWriteArrayList<HttpEventListener>()

    /**
     * Adds an [HttpEventListener] which upstream services can provide to receive updates on important events.
     */
    fun addListener(eventListener: HttpEventListener) {
        eventListeners.add(eventListener)
    }

    fun removeListener(eventListener: HttpEventListener) {
        eventListeners.remove(eventListener)
    }

    init {
        followStatusChanges(configurationService).also {
            executeBeforeClose(it::close)
        }
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

    private inner class ServerChannelInitializer : ChannelInitializer<SocketChannel>() {

        private val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())

        init {
            configurationService.configuration.sslConfig.run {
                keyManagerFactory.init(this.keyStore, this.keyStorePassword.toCharArray())
            }
        }

        override fun initChannel(ch: SocketChannel) {
            val pipeline = ch.pipeline()
            pipeline.addLast("sslHandler", createServerSslHandler(configurationService.configuration.sslConfig.keyStore, keyManagerFactory))
            pipeline.addLast("idleStateHandler", IdleStateHandler(0, 0, SERVER_IDLE_TIME_SECONDS))
            pipeline.addLast(HttpServerCodec())
            pipeline.addLast(HttpChannelHandler(this@HttpServer, logger))
        }
    }

    override fun onStart() {
        configurationService.start()
        onStatusChange(configurationService.status)
    }

    override fun onStatusChange(newStatus: LifecycleStatus) {
        if ((configurationService.status == LifecycleStatus.UP) && (status != LifecycleStatus.UP)) {
            logger.info("Starting HTTP Server")
            val bossGroup = NioEventLoopGroup(1).also {
                executeBeforeStop {
                    it.shutdownGracefully()
                    it.terminationFuture().sync()
                }
            }
            val workerGroup = NioEventLoopGroup(NUM_SERVER_THREADS).also {
                executeBeforeStop {
                    it.shutdownGracefully()
                    it.terminationFuture().sync()
                }
            }

            val server = ServerBootstrap()
            server.group(bossGroup, workerGroup).channel(NioServerSocketChannel::class.java)
                .childHandler(ServerChannelInitializer())
            val host = configurationService.configuration.hostAddress
            val port = configurationService.configuration.hostPort
            logger.info("Trying to bind to $host:$port")
            val channelFuture = server.bind(host, port).sync()
            logger.info("Listening on port $port")
            channelFuture.channel().also { serverChannel ->
                executeBeforeStop {
                    if (serverChannel.isOpen) {
                        serverChannel.close().sync()
                    }
                }
                serverChannel.closeFuture().addListener {
                    if (isRunning) {
                        close()
                    }
                }
            }
            executeBeforeStop {
                clientChannels.clear()
            }
            status = LifecycleStatus.UP
            logger.info("HTTP Server started")
        }
    }

    // YIFT: Remove?
    fun startAndWaitForStarted() {
        start()
        while (status != LifecycleStatus.UP) {
            Thread.sleep(100)
        }
    }
    // YIFT: Remove?
    fun stopAndWaitForDestruction() {
        stop()
        while (status != LifecycleStatus.DOWN) {
            Thread.sleep(100)
        }
    }
}
