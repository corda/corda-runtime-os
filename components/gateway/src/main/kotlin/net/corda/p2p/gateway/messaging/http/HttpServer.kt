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
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import net.corda.lifecycle.LifeCycle
import net.corda.p2p.gateway.messaging.SslConfiguration
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.lang.IllegalStateException
import java.net.BindException
import java.net.SocketAddress
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
 *
 * @param host [String] value representing a host name or IP address used when binding the server
 * @param port port number used when binding the server
 * @param sslConfig the configuration to be used for the one-way TLS handshake
 * @param traceLogging optional setting to enable Netty logging inside the channel pipeline. Should be set to *true* only when debugging
 */
class HttpServer(private val host: String,
                 private val port: Int,
                 private val sslConfig: SslConfiguration,
                 private val traceLogging: Boolean = false) : LifeCycle {

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

    private var started = false
    override val isRunning: Boolean
        get() = started

    private val _onReceive = PublishSubject.create<HttpMessage>().toSerialized()
    val onReceive: Observable<HttpMessage>
        get() = _onReceive

    private val _onConnection = PublishSubject.create<ConnectionChangeEvent>().toSerialized()
    val onConnection: Observable<ConnectionChangeEvent>
        get() = _onConnection

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
            pipeline.addLast("sslHandler", createServerSslHandler(parent.sslConfig.keyStore, keyManagerFactory))
            if (parent.traceLogging) pipeline.addLast("logger", LoggingHandler(LogLevel.INFO))
            pipeline.addLast(HttpRequestDecoder())
            pipeline.addLast(HttpResponseEncoder())
            pipeline.addLast(HttpChannelHandler(
                onOpen = { channel, change ->
                    parent.run {
                        clientChannels[channel.remoteAddress()] = channel
                        _onConnection.onNext(change)
                    }
                },
                onClose = { channel, change ->
                    parent.run {
                        clientChannels.remove(channel.remoteAddress())
                        _onConnection.onNext(change)
                    }
                },
                onReceive = { msg ->
                    parent._onReceive.onNext(msg)
                }
            ))
        }
    }
}