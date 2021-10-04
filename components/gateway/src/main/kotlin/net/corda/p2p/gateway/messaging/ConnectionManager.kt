package net.corda.p2p.gateway.messaging

import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import net.corda.configuration.read.ConfigurationReadService
import net.corda.p2p.gateway.GatewayConfigurationService
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
    parent: DominoTile,
    configurationReaderService: ConfigurationReadService,
    private val listener: HttpEventListener,
) : DominoTile(parent),
    GatewayConfigurationService.ReconfigurationListener {

    companion object {
        private val logger = LoggerFactory.getLogger(ConnectionManager::class.java)

        private const val NUM_CLIENT_WRITE_THREADS = 2
        private const val NUM_CLIENT_NETTY_THREADS = 2
    }
    private val configurationService = GatewayConfigurationService(this, configurationReaderService, this)
    override val children = listOf(configurationService)

    private val clientPool = ConcurrentHashMap<URI, HttpClient>()
    private var writeGroup: EventLoopGroup? = null
    private var nettyGroup: EventLoopGroup? = null

    /**
     * Return an existing or new [HttpClient].
     * @param destinationInfo the [DestinationInfo] object containing the destination's URI, SNI, and legal name
     */
    fun acquire(destinationInfo: DestinationInfo): HttpClient {
        return clientPool.computeIfAbsent(destinationInfo.uri) {
            val client = HttpClient(
                destinationInfo,
                configurationService.configuration.sslConfig,
                writeGroup!!,
                nettyGroup!!,
                listener
            )
            executeBeforeStop(client::close)
            client.start()
            client
        }
    }

    override fun startSequence() {
        NioEventLoopGroup(NUM_CLIENT_WRITE_THREADS).also {
            executeBeforeStop {
                it.shutdownGracefully()
                it.terminationFuture().sync()
            }
        }.also { writeGroup = it }
        nettyGroup = NioEventLoopGroup(NUM_CLIENT_NETTY_THREADS).also {
            executeBeforeStop {
                it.shutdownGracefully()
                it.terminationFuture().sync()
            }
        }
        executeBeforeStop(clientPool::clear)
        state = State.Running
    }

    override fun gotNewConfiguration(newConfiguration: GatewayConfiguration, oldConfiguration: GatewayConfiguration) {
        if (newConfiguration.sslConfig != oldConfiguration.sslConfig) {
            logger.info("Got new SSL configuration, recreating the clients pool")
            val oldClients = clientPool.toMap()
            clientPool.clear()
            oldClients.values.forEach {
                it.close()
            }
        }
    }
}
