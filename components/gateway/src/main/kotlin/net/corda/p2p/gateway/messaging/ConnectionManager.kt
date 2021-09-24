package net.corda.p2p.gateway.messaging

import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import net.corda.p2p.gateway.GatewayConfigurationService
import net.corda.p2p.gateway.domino.LifecycleWithCoordinator
import net.corda.p2p.gateway.domino.LifecycleWithCoordinatorAndResources
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
    parent: LifecycleWithCoordinator,
    private val configurationService: GatewayConfigurationService,
) : LifecycleWithCoordinatorAndResources(parent),
    GatewayConfigurationService.ReconfigurationListener {

    companion object {
        private val logger = LoggerFactory.getLogger(ConnectionManager::class.java)

        private const val NUM_CLIENT_WRITE_THREADS = 2
        private const val NUM_CLIENT_NETTY_THREADS = 2
    }

    private val clientPool = ConcurrentHashMap<URI, HttpClient>()
    private var writeGroup: EventLoopGroup? = null
    private var nettyGroup: EventLoopGroup? = null

    private val eventListeners = ConcurrentHashMap.newKeySet<HttpEventListener>()

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
            val client = HttpClient(destinationInfo, configurationService.configuration.sslConfig, writeGroup!!, nettyGroup!!)
            executeBeforePause(client::close)
            eventListeners.forEach { client.addListener(it) }
            client.start()
            client
        }
    }

    override fun openSequence() {
        followStatusChanges(configurationService).also {
            executeBeforeClose(it::close)
        }
        configurationService.listenToReconfigurations(this)
        executeBeforeClose {
            configurationService.stopListenToReconfigurations(this)
        }
    }

    override fun resumeSequence() {
        logger.info("Starting connection manager")
        configurationService.start()
        onStatusUp()
    }

    override fun onStatusUp() {
        if ((configurationService.state == State.Up) && (state != State.Up)) {
            NioEventLoopGroup(NUM_CLIENT_WRITE_THREADS).also {
                executeBeforePause {
                    it.shutdownGracefully()
                    it.terminationFuture().sync()
                }
            }.also { writeGroup = it }
            nettyGroup = NioEventLoopGroup(NUM_CLIENT_NETTY_THREADS).also {
                executeBeforePause {
                    it.shutdownGracefully()
                    it.terminationFuture().sync()
                }
            }
            executeBeforePause(clientPool::clear)
            state = State.Up
        }
    }

    override fun onStatusDown() {
        stop()
    }

    override fun gotNewConfiguration(newConfiguration: GatewayConfiguration, oldConfiguration: GatewayConfiguration) {
        if (newConfiguration.sslConfig != oldConfiguration.sslConfig) {
            logger.info("Got new SSL configuraion, recreating the clients pool")
            val oldClients = clientPool.toMap()
            clientPool.clear()
            oldClients.values.forEach {
                it.close()
            }
        }
    }
}
