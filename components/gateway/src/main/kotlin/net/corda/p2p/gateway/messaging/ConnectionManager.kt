package net.corda.p2p.gateway.messaging

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalListener
import io.netty.channel.ConnectTimeoutException
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import net.corda.lifecycle.LifeCycle
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.v5.base.util.NetworkHostAndPort
import org.slf4j.LoggerFactory
import java.lang.NullPointerException
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
 * TODO: need to figure out how to handle situations where pool is maxed out and no stale connections can be evicted
 *
 */
class ConnectionManager(private val sslConfiguration: SslConfiguration,
                        private val config: ConnectionManagerConfig = ConnectionManagerConfig(MAX_CONNECTIONS, ACQUIRE_TIMEOUT, CONNECTION_MAX_IDLE_TIME)) :
    LifeCycle {

    companion object {
        /**
         * Maximum size of the connection pool
         */
        private const val MAX_CONNECTIONS = 100L

        /**
         * Time in milliseconds after which a connection request will fail
         */
        private const val ACQUIRE_TIMEOUT = 60000L

        /**
         * Time in milliseconds after which an inactive connection in the pool will be released (closed)
         */
        private const val CONNECTION_MAX_IDLE_TIME = 60000L
    }

    private val logger = LoggerFactory.getLogger(ConnectionManager::class.java)

    private val connectionPool = Caffeine.newBuilder()
        .maximumSize(config.connectionPoolSize)
        .expireAfterAccess(config.maxIdleTime, TimeUnit.MILLISECONDS)
        .removalListener(RemovalListener<NetworkHostAndPort, HttpClient> { key, value, cause ->
            logger.info("Removing entry for key $key. Reason: $cause")
            value?.close()
        })
        //TODO: replace with scheduled task to do clean-up every now and then. With Runnable::run, clean-up happens when cache is used
        .executor(Runnable::run) //Specify an executor thread for async background tasks such as clean-up of expired connections
        .build<NetworkHostAndPort, HttpClient>()

    private var sharedEventLoopGroup: EventLoopGroup? = null

    private var started = false
    override val isRunning: Boolean
        get() = started

    override fun start() {
        sharedEventLoopGroup = NioEventLoopGroup(4)
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
     * @param remoteAddress the [NetworkHostAndPort] to connect to
     * @param sslConfig the transport configuration
     * @throws [TimeoutException] if a successful connection cannot established
     */
    @Throws(ConnectTimeoutException::class)
    fun acquire(remoteAddress: NetworkHostAndPort): HttpClient {
        logger.info("Acquiring connection for remote address $remoteAddress")
        return connectionPool.get(remoteAddress) {
            logger.info("Creating new connection to $remoteAddress")
            // Try to connect. If unsuccessful in the specified time, return something...
            val client = HttpClient(remoteAddress, sslConfiguration, sharedEventLoopGroup)
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
                throw ConnectTimeoutException("Could not acquire connection to $remoteAddress in ${config.acquireTimeout} milliseconds")
            }
            client
        }!!
    }

    /**
     * Clears the cached connections corresponding to the given remote address
     * @param remoteAddress the [NetworkHostAndPort] of the peer
     */
    fun dispose(remoteAddress: NetworkHostAndPort) {
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
     * @param remoteAddress the
     */
    fun activeConnectionsForHost(remoteAddress: NetworkHostAndPort) =
        connectionPool.asMap()
            .entries
            .filter { e -> e.key == remoteAddress }
            .size
}

data class ConnectionManagerConfig(val connectionPoolSize: Long, val acquireTimeout: Long, val maxIdleTime: Long)