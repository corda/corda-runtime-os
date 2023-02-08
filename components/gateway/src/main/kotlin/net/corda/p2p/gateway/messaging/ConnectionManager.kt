package net.corda.p2p.gateway.messaging

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.netty.channel.nio.NioEventLoopGroup
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.lifecycle.Resource
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import java.util.concurrent.TimeUnit

/**
 * The [ConnectionManager] is responsible for creating an HTTP connection and caching it. If a connection to the requested
 * target already exists, it's reused. There will be a maximum 100 connections allowed at any given time. Any new requests
 * will block until resources become available.
 *
 * To ensure we don't block indefinitely, several timeouts will be used to determine when to close an inactive connection
 * or to drop a request for one.
 *
 */
internal class ConnectionManager(
    private val sslConfiguration: SslConfiguration,
    private val connectionConfiguration: ConnectionConfiguration,
    nioEventLoopGroupFactory: (Int) -> NioEventLoopGroup = { NioEventLoopGroup(it) }
) : Resource {

    companion object {
        private const val NUM_CLIENT_WRITE_THREADS = 2
        private const val NUM_CLIENT_NETTY_THREADS = 2
    }

    private val clientPool: Cache<DestinationInfo, HttpClient> = CacheFactoryImpl().build(
        "P2P-Client-Pool",
        Caffeine.newBuilder()
            .maximumSize(connectionConfiguration.maxClientConnections)
            .expireAfterAccess(connectionConfiguration.connectionIdleTimeout)
            .removalListener { _, value, _ -> value?.close() })
    private var writeGroup = nioEventLoopGroupFactory(NUM_CLIENT_WRITE_THREADS)
    private var nettyGroup = nioEventLoopGroupFactory(NUM_CLIENT_NETTY_THREADS)

    /**
     * Return an existing or new [HttpClient].
     * @param destinationInfo the [DestinationInfo] object containing the destination's URI, SNI, and legal name
     */
    fun acquire(destinationInfo: DestinationInfo): HttpClient {
        return clientPool.get(destinationInfo) {
            val client = HttpClient(
                destinationInfo,
                sslConfiguration,
                writeGroup,
                nettyGroup,
                connectionConfiguration,
            )
            client.start()
            client
        }
    }

    override fun close() {
        clientPool.invalidateAll()
        // Using short quiet period (100 ms) - all the clients had been closed.
        val shutdownWriteGroup = writeGroup.shutdownGracefully(100, 15000, TimeUnit.MILLISECONDS)
        val shutdownNettyGroup = nettyGroup.shutdownGracefully(100, 15000, TimeUnit.MILLISECONDS)
        shutdownWriteGroup.sync()
        shutdownNettyGroup.sync()
    }
}
