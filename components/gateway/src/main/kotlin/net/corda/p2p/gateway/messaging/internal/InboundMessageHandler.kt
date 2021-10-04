package net.corda.p2p.gateway.messaging.internal

import com.typesafe.config.ConfigFactory
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.LinkInMessage
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.InitiatorHelloMessage
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.gateway.Gateway.Companion.PUBLISHER_ID
import net.corda.p2p.gateway.GatewayConfigurationService
import net.corda.p2p.gateway.domino.DominoTile
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import net.corda.p2p.gateway.messaging.http.HttpMessage
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.p2p.gateway.messaging.session.SessionPartitionMapperImpl
import net.corda.p2p.schema.Schema.Companion.LINK_IN_TOPIC
import net.corda.v5.base.util.contextLogger
import java.io.IOException
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * This class implements a simple message processor for p2p messages received from other Gateways.
 */
internal class InboundMessageHandler(
    parent: DominoTile,
    configurationReaderService: ConfigurationReadService,
    private val publisherFactory: PublisherFactory,
    subscriptionFactory: SubscriptionFactory,
) : GatewayConfigurationService.ReconfigurationListener,
    Lifecycle,
    HttpEventListener,
    DominoTile(parent) {

    companion object {
        private val logger = contextLogger()
    }

    private var p2pInPublisher: Publisher? = null
    private val sessionPartitionMapper = SessionPartitionMapperImpl(this, subscriptionFactory)
    private val configurationService = GatewayConfigurationService(this, configurationReaderService, this)

    @Volatile
    private var httpServer: HttpServer? = null
    private val serverLock = ReentrantReadWriteLock()

    override fun startSequence() {
        logger.info("Starting P2P message receiver")

        val publisherConfig = PublisherConfig(PUBLISHER_ID)
        val publisher = publisherFactory.createPublisher(publisherConfig, ConfigFactory.empty())
        executeBeforeStop {
            publisher.close()
        }
        p2pInPublisher = publisher

        if (httpServer?.isRunning != true) {
            serverLock.write {
                if (httpServer?.isRunning != true) {
                    logger.info(
                        "Starting HTTP server for $name to " +
                            "${configurationService.configuration.hostAddress}:${configurationService.configuration.hostPort}"
                    )
                    val newServer = HttpServer(this, configurationService.configuration)
                    newServer.start()
                    executeBeforeStop(newServer::stop)
                    httpServer = newServer
                }
            }
        }

        logger.info("Started P2P message receiver")
        state = State.Running
    }

    private fun writeResponse(status: HttpResponseStatus, address: SocketAddress) {
        serverLock.read {
            val server = httpServer ?: throw IllegalStateException("Server is not ready")
            server.write(status, ByteArray(0), address)
        }
    }

    /**
     * Handler for direct P2P messages. The payload is deserialized and then published to the ingress topic.
     * A session init request has additional handling as the Gateway needs to generate a secret and share it
     */
    override fun onMessage(message: HttpMessage) {
        if (!isRunning) {
            logger.error("Received message from ${message.source}, while handler is stopped. Discarding it and returning error code.")
            writeResponse(HttpResponseStatus.SERVICE_UNAVAILABLE, message.source)
            return
        }

        if (message.statusCode != HttpResponseStatus.OK) {
            logger.warn("Received invalid request from ${message.source}. Status code ${message.statusCode}")
            writeResponse(message.statusCode, message.source)
            return
        }

        logger.debug("Processing request message from ${message.source}")
        val p2pMessage = try {
            LinkInMessage.fromByteBuffer(ByteBuffer.wrap(message.payload))
        } catch (e: IOException) {
            logger.warn("Invalid message received. Cannot deserialize")
            logger.debug(e.stackTraceToString())
            writeResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, message.source)
            return
        }

        logger.debug("Received message of type ${p2pMessage.schema.name}")
        when (p2pMessage.payload) {
            is UnauthenticatedMessage -> {
                p2pInPublisher!!.publish(listOf(Record(LINK_IN_TOPIC, generateKey(), p2pMessage)))
                writeResponse(HttpResponseStatus.OK, message.source)
            }
            else -> {
                val statusCode = processSessionMessage(p2pMessage)
                writeResponse(statusCode, message.source)
            }
        }
    }

    private fun processSessionMessage(p2pMessage: LinkInMessage): HttpResponseStatus {
        val sessionId = getSessionId(p2pMessage) ?: return HttpResponseStatus.INTERNAL_SERVER_ERROR

        val partitions = sessionPartitionMapper.getPartitions(sessionId)
        return if (partitions == null) {
            logger.warn("No mapping for session ($sessionId), discarding the message and returning an error.")
            HttpResponseStatus.INTERNAL_SERVER_ERROR
        } else {
            // this is simplistic (stateless) load balancing amongst the partitions owned by the LM that "hosts" the session.
            val selectedPartition = partitions.random()
            val record = Record(LINK_IN_TOPIC, sessionId, p2pMessage)
            p2pInPublisher?.publishToPartition(listOf(selectedPartition to record))
            HttpResponseStatus.OK
        }
    }

    private fun getSessionId(message: LinkInMessage): String? {
        return when (message.payload) {
            is AuthenticatedDataMessage -> (message.payload as AuthenticatedDataMessage).header.sessionId
            is AuthenticatedEncryptedDataMessage -> (message.payload as AuthenticatedEncryptedDataMessage).header.sessionId
            is InitiatorHelloMessage -> (message.payload as InitiatorHelloMessage).header.sessionId
            is InitiatorHandshakeMessage -> (message.payload as InitiatorHandshakeMessage).header.sessionId
            is ResponderHelloMessage -> (message.payload as ResponderHelloMessage).header.sessionId
            is ResponderHandshakeMessage -> (message.payload as ResponderHandshakeMessage).header.sessionId
            is UnauthenticatedMessage -> {
                logger.warn("No session associated with ${UnauthenticatedMessage::class.java}")
                return null
            }
            else -> {
                logger.warn("Invalid payload of LinkInMessage: ${message.payload::class.java}")
                return null
            }
        }
    }

    private fun generateKey(): String {
        return UUID.randomUUID().toString()
    }

    override fun gotNewConfiguration(newConfiguration: GatewayConfiguration, oldConfiguration: GatewayConfiguration) {
        if (newConfiguration.hostPort == oldConfiguration.hostPort) {
            logger.info("New server configuration for $name on the same port, HTTP server will have to go down")
            serverLock.write {
                val oldServer = httpServer
                httpServer = null
                oldServer?.stop()
                val newServer = HttpServer(this, newConfiguration)
                newServer.start()
                executeBeforeStop(newServer::stop)
                httpServer = newServer
            }
        } else {
            logger.info("New server configuration, $name will be connected to ${newConfiguration.hostAddress}:${newConfiguration.hostPort}")
            val newServer = HttpServer(this, newConfiguration)
            newServer.start()
            executeBeforeStop(newServer::stop)
            serverLock.write {
                val oldServer = httpServer
                httpServer = null
                oldServer?.stop()
                httpServer = newServer
            }
        }
    }

    override val children = listOf(configurationService, sessionPartitionMapper)
}
