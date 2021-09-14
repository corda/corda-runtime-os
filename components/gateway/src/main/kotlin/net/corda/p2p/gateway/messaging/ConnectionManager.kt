package net.corda.p2p.gateway.messaging

import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import net.corda.p2p.gateway.domino.CloseableMap
import net.corda.p2p.gateway.domino.CloseableNioEventLoopGroup
import net.corda.p2p.gateway.domino.DominoCoordinatorFactory
import net.corda.p2p.gateway.domino.DominoTile
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * The [ConnectionManager] is responsible for creating an HTTP connection and caching it. If a connection to the requested
 * target already exists, it's reused. There will be a maximum 100 connections allowed at any given time. Any new requests
 * will block until resources become available.
 *
 * To ensure we don't block indefinitely, several timeouts will be used to determine when to close an inactive connection
 * or to drop a request for one.
 *
 */
class ConnectionManager(
    dominoCoordinatorFactory: DominoCoordinatorFactory,
    private val sslConfiguration: SslConfiguration,
) : DominoTile(dominoCoordinatorFactory) {

    companion object {
        private val logger = LoggerFactory.getLogger(ConnectionManager::class.java)

        private const val NUM_CLIENT_WRITE_THREADS = 2
        private const val NUM_CLIENT_NETTY_THREADS = 2
    }

    private val clientPool = ConcurrentHashMap<URI, HttpClient>()
    private var writeGroup: EventLoopGroup? = null
    private var nettyGroup: EventLoopGroup? = null

    private val eventListeners = ConcurrentHashMap.newKeySet<HttpEventListener>()

    override fun prepareResources() {
        logger.info("Starting connection manager")

        writeGroup = NioEventLoopGroup(NUM_CLIENT_WRITE_THREADS)
        keepResource(CloseableNioEventLoopGroup(writeGroup!!))
        nettyGroup = NioEventLoopGroup(NUM_CLIENT_NETTY_THREADS)
        keepResource(CloseableNioEventLoopGroup(nettyGroup!!))
        keepResource(CloseableMap(clientPool))
    }

    fun addListener(eventListener: HttpEventListener) {
        eventListeners.add(eventListener)
        clientPool.forEach { it.value.addListener(eventListener) }
    }

    fun removeListener(eventListener: HttpEventListener) {
        eventListeners.remove(eventListener)
        clientPool.forEach { it.value.removeListener(eventListener) }
    }

    /**
     * Return an existing or new [HttpClient].
     * @param destinationInfo the [DestinationInfo] object containing the destination's URI, SNI, and legal name
     */
    fun acquire(destinationInfo: DestinationInfo): HttpClient {
        return clientPool.computeIfAbsent(destinationInfo.uri) {
            val client = HttpClient(destinationInfo, sslConfiguration, writeGroup!!, nettyGroup!!)
            keepResource(client)
            eventListeners.forEach { client.addListener(it) }
            client.start()
            client
        }
    }
}
