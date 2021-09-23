package net.corda.p2p.gateway

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.gateway.domino.LifecycleWithCoordinator
import net.corda.p2p.gateway.domino.LifecycleWithCoordinatorAndResources
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.p2p.gateway.messaging.internal.InboundMessageHandler
import net.corda.p2p.gateway.messaging.session.SessionPartitionMapperImpl
import net.corda.v5.base.util.contextLogger
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class Server(
    parent: LifecycleWithCoordinator,
    private val configurationService: GatewayConfigurationService,
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory
) : LifecycleWithCoordinatorAndResources(
    parent
),
    GatewayConfigurationService.ReconfigurationListener {
    companion object {
        private val logger = contextLogger()
    }

    private val eventListeners = ConcurrentHashMap.newKeySet<HttpEventListener>()
    @Volatile
    private var httpServer: HttpServer? = null
    private val serverLock = ReentrantReadWriteLock()
    private val sessionPartitionMapper = SessionPartitionMapperImpl(this, subscriptionFactory)
    private val inboundMessageProcessor = InboundMessageHandler(this, publisherFactory, this, sessionPartitionMapper)

    fun addListener(eventListener: HttpEventListener) {
        eventListeners.add(eventListener)
    }

    fun removeListener(eventListener: HttpEventListener) {
        eventListeners.remove(eventListener)
    }

    fun write(statusCode: HttpResponseStatus, message: ByteArray, destination: SocketAddress) {
        serverLock.read {
            val server = httpServer ?: throw IllegalStateException("Server is not ready")
            server.write(statusCode, message, destination)
        }
    }

    override fun onStatusUp() {
        if (configurationService.state != State.Up) {
            return
        }
        if (httpServer == null) {
            serverLock.write {
                if (httpServer == null) {
                    logger.info(
                        "Starting HTTP server for $name to " +
                            "${configurationService.configuration.hostAddress}:${configurationService.configuration.hostPort}"
                    )
                    val newServer = HttpServer(eventListeners, configurationService.configuration)
                    newServer.start()
                    executeBeforePause(newServer::stop)
                    httpServer = newServer
                }
            }
        }
        if (sessionPartitionMapper.state != State.Up) {
            sessionPartitionMapper.start()
            return
        }
        if (inboundMessageProcessor.state != State.Up) {
            inboundMessageProcessor.start()
            return
        }
        state = State.Up
    }

    override fun onStatusDown() {
        stop()
    }

    init {
        // YIFT: Why can't I follow all of them together?!
        listOf(
            configurationService,
            sessionPartitionMapper,
            inboundMessageProcessor
        )
            .map {
                followStatusChanges(it)
            }.forEach {
                executeBeforeClose(it::close)
            }

        configurationService.listenToReconfigurations(this)
        executeBeforeClose {
            configurationService.stopListenToReconfigurations(this)
        }
    }

    override fun resumeSequence() {
        listOf(
            sessionPartitionMapper,
            inboundMessageProcessor
        ).forEach {
            executeBeforePause(it::stop)
        }

        onStatusUp()
    }

    override fun gotNewConfiguration(newConfiguration: GatewayConfiguration, oldConfiguration: GatewayConfiguration) {
        if (newConfiguration.hostPort == oldConfiguration.hostPort) {
            logger.info("New server configuration for $name on the same port, HTTP server will have to go down")
            serverLock.write {
                val oldServer = httpServer
                httpServer = null
                oldServer?.stop()
                val newServer = HttpServer(eventListeners, newConfiguration)
                newServer.start()
                executeBeforePause(newServer::stop)
                httpServer = newServer
            }
        } else {
            logger.info("New server configuration, $name will be connected to ${newConfiguration.hostAddress}:${newConfiguration.hostPort}")
            val newServer = HttpServer(eventListeners, newConfiguration)
            newServer.start()
            executeBeforePause(newServer::stop)
            serverLock.write {
                val oldServer = httpServer
                httpServer = null
                oldServer?.stop()
                httpServer = newServer
            }
        }
    }
}
