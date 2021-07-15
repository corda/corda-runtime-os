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
import net.corda.p2p.gateway.messaging.HttpMessage
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.v5.base.util.NetworkHostAndPort
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.lang.IllegalStateException
import java.net.BindException
import java.net.InetSocketAddress
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
class HttpServer(private val hostAddress: NetworkHostAndPort, private val sslConfig: SslConfiguration) : LifeCycle {

    private val logger = LoggerFactory.getLogger(HttpServer::class.java)

    companion object {
        /**
         * Default number of thread to use for the worker group
         */
        private const val NUM_SERVER_THREADS = 4
    }

    private val lock = ReentrantLock()
    @Volatile
    private var stopping: Boolean = false
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var serverChannel: Channel? = null
    private val clientChannels = ConcurrentHashMap<InetSocketAddress, SocketChannel>()

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
//                .handler(LoggingHandler(LogLevel.INFO))
                .childHandler(ServerChannelInitializer(this))
            logger.info("Trying to bind to ${hostAddress.port}")
            val channelFuture = server.bind(hostAddress.host, hostAddress.port).sync()
            logger.info("Listening on port ${hostAddress.port}")
            serverChannel = channelFuture.channel()
            started = true
        }
    }

    override fun stop() {
        lock.withLock {
            try {
                logger.info("Stopping HTTP server")
                stopping = true
                serverChannel?.apply { close() }
                serverChannel = null

                workerGroup?.shutdownGracefully()
                workerGroup?.terminationFuture()?.sync()

                bossGroup?.shutdownGracefully()
                bossGroup?.terminationFuture()?.sync()

                workerGroup = null
                bossGroup = null
            } finally {
                stopping = false
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
    fun write(statusCode: HttpResponseStatus, message: ByteArray, destination: NetworkHostAndPort) {
        val channel = clientChannels[InetSocketAddress(destination.host, destination.port)]
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