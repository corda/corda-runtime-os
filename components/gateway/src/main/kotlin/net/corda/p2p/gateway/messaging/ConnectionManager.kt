package net.corda.p2p.gateway.messaging

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalListener
import io.netty.channel.ConnectTimeoutException
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import net.corda.lifecycle.LifeCycle
import net.corda.p2p.gateway.messaging.http.HttpClient
import org.slf4j.LoggerFactory
import java.lang.NullPointerException
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.stream.Collectors

/**
 * The [ConnectionManager] is responsible for creating an HTTP connection and caching it. If a connection to the requested
 * target already exists, it's reused. There will be a maximum 100 connections allowed at any given time. Any new requests
 * will block until resources become available.
 *
 * To ensure we don't block indefinitely, several timeouts will be used to determine when to close an inactive connection
 * or to drop a request for one.
 *
 */
class ConnectionManager(private val sslConfiguration: SslConfiguration,
                        val config: ConnectionConfiguration) : LifeCycle {

    companion object {
        private val logger = LoggerFactory.getLogger(ConnectionManager::class.java)
    }

    private val connectionPool = Caffeine.newBuilder()
        .maximumSize(config.maxClientConnections)
        .expireAfterAccess(config.connectionIdleTimeout, TimeUnit.MILLISECONDS)
        .removalListener(RemovalListener<URI, HttpClient> { key, value, cause ->
            logger.debug("Removing connection for target $key. Reason: $cause")
            value?.close()
        })
        //T0DO: replace with scheduled task to do clean-up every now and then. With Runnable::run, clean-up happens when cache is used
        .executor(Runnable::run) //Specify an executor thread for async background tasks such as clean-up of expired connections
        .build<URI, HttpClient>()

    private var sharedEventLoopGroup: EventLoopGroup? = null

    private var started = false
    override val isRunning: Boolean
        get() = started

    override fun start() {
        sharedEventLoopGroup = NioEventLoopGroup(4)
        started = true
    }

    override fun stop() {
        logger.info("Stopping")
        started = false
        connectionPool.invalidateAll()
        connectionPool.cleanUp()
        sharedEventLoopGroup?.shutdownGracefully()
        sharedEventLoopGroup?.terminationFuture()?.sync()
        sharedEventLoopGroup = null
        logger.info("Stopped")
    }


    /**
     * Return an existing or new [HttpClient]. The client will be started connected to the specified remote address. This
     * method blocks until either it returns or throws.
     * @param target the [URI] to connect to
     * @throws [TimeoutException] if a successful connection cannot established
     */
    @Throws(ConnectTimeoutException::class)
    fun acquire(target: URI, sni: String): HttpClient {
        return connectionPool.get(target) {
            logger.info("Creating new connection to ${target.authority}")
            val client = HttpClient(target, sni, sslConfiguration, sharedEventLoopGroup)
            val connectionLock = CountDownLatch(1)
            val connectionSub = client.onConnection.subscribe { evt ->
                if (evt.connected) {
                    connectionLock.countDown()
                }
            }
            client.start()
            val connected = connectionLock.await(config.acquireTimeout, TimeUnit.MILLISECONDS)
            connectionSub.unsubscribe()
            if (!connected) {
                throw ConnectTimeoutException("Could not acquire connection to ${target.authority} " +
                        "in ${config.acquireTimeout} milliseconds")
            }
            client
        }!!
    }

    /**
     * Clears the cached connections corresponding to the given remote address
     * @param remoteAddress the [SocketAddress] of the peer
     */
    @Suppress("TooGenericExceptionCaught")
    fun dispose(remoteAddress: URI) {
        val toDispose = connectionPool.asMap()
            .entries
            .stream()
            .filter { e -> e.key == remoteAddress }
            .collect(Collectors.toList())
        toDispose.forEach { entry ->
            try {
                logger.info("Disposing connection for ${entry.key}")
                connectionPool.invalidate(entry.key)
                entry.value.close()
            } catch (ex: NullPointerException) {
                logger.warn("Could not remove connection for ${entry.key}")
            }
        }
    }

    /**
     * Returns the current number of active connections. Does not count connections which are being started or at the time
     * of the call.
     */
    fun activeConnections() = connectionPool.estimatedSize()

    /**
     * Returns the current number of active connections for the given host address. Does not count connections which
     * are being started at the time of the call.
     * @param remoteAddress the [SocketAddress] of the peer
     */
    fun activeConnectionsForHost(remoteAddress: URI) =
        connectionPool.asMap()
            .entries
            .filter { e -> e.key == remoteAddress && e.value?.connected!! }
            .size
}