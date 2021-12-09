package net.corda.p2p.gateway.messaging.internal

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.p2p.gateway.GatewayMessage
import net.corda.data.p2p.gateway.GatewayResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
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
import net.corda.p2p.gateway.messaging.http.HttpRequest
import net.corda.p2p.gateway.messaging.http.HttpServerListener
import net.corda.p2p.gateway.messaging.http.ReconfigurableHttpServer
import net.corda.p2p.gateway.messaging.session.SessionPartitionMapperImpl
import net.corda.p2p.schema.Schema.Companion.LINK_IN_TOPIC
import net.corda.v5.base.util.contextLogger
import java.nio.ByteBuffer
import java.util.*

/**
 * This class implements a simple message processor for p2p messages received from other Gateways.
 */
@Suppress("LongParameterList")
internal class InboundMessageHandler(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    configurationReaderService: ConfigurationReadService,
    publisherFactory: PublisherFactory,
    subscriptionFactory: SubscriptionFactory,
    nodeConfiguration: SmartConfig,
    instanceId: Int,
    ) : HttpServerListener, LifecycleWithDominoTile {

    companion object {
        private val logger = contextLogger()
    }

    private var p2pInPublisher = PublisherWithDominoLogic(
        publisherFactory,
        lifecycleCoordinatorFactory,
        PublisherConfig("inbound-message-handler"),
        nodeConfiguration
    )
    private val sessionPartitionMapper = SessionPartitionMapperImpl(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        nodeConfiguration,
        instanceId
    )
    private val server = ReconfigurableHttpServer(lifecycleCoordinatorFactory, configurationReaderService, this)
    override val dominoTile = DominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        children = listOf(sessionPartitionMapper.dominoTile, p2pInPublisher.dominoTile, server.dominoTile)
    )

    /**
     * Handler for direct P2P messages. The payload is deserialized and then published to the ingress topic.
     * A session init request has additional handling as the Gateway needs to generate a secret and share it
     */
    override fun onRequest(request: HttpRequest) {
        dominoTile.withLifecycleLock { handleRequest(request) }
    }

    private fun handleRequest(request: HttpRequest) {
        if (!isRunning) {
            logger.error("Received message from ${request.source}, while handler is stopped. Discarding it and returning error code.")
            server.writeResponse(HttpResponseStatus.SERVICE_UNAVAILABLE, request.source)
            return
        }

        val (gatewayMessage, p2pMessage) = try {
            val gatewayMessage = GatewayMessage.fromByteBuffer(ByteBuffer.wrap(request.payload))
            gatewayMessage to LinkInMessage(gatewayMessage.payload)
        } catch (e: Throwable) {
            logger.warn("Invalid message received. Cannot deserialize")
            logger.debug(e.stackTraceToString())
            server.writeResponse(HttpResponseStatus.BAD_REQUEST, request.source)
            return
        }

        logger.debug("Received and processing message ${gatewayMessage.id} of type ${p2pMessage.payload.javaClass} from ${request.source}")
        val response = GatewayResponse(gatewayMessage.id)
        when (p2pMessage.payload) {
            is UnauthenticatedMessage -> {
                p2pInPublisher.publish(listOf(Record(LINK_IN_TOPIC, generateKey(), p2pMessage)))
                server.writeResponse(HttpResponseStatus.OK, request.source, response.toByteBuffer().array())
            }
            else -> {
                val statusCode = processSessionMessage(p2pMessage)
                server.writeResponse(statusCode, request.source, response.toByteBuffer().array())
            }
        }
    }

    private fun processSessionMessage(p2pMessage: LinkInMessage): HttpResponseStatus {
        if (p2pMessage.payload is InitiatorHelloMessage) {
            p2pInPublisher.publish(listOf(Record(LINK_IN_TOPIC, UUID.randomUUID().toString(), p2pMessage)))
            return HttpResponseStatus.OK
        }

        val sessionId = getSessionId(p2pMessage) ?: return HttpResponseStatus.INTERNAL_SERVER_ERROR
        val partitions = sessionPartitionMapper.getPartitions(sessionId)
        println("QQQ Asked for $sessionId got $partitions")
        return if (partitions == null) {
            logger.warn("No mapping for session ($sessionId), discarding the message and returning an error.")
            HttpResponseStatus.INTERNAL_SERVER_ERROR
        } else {
            // this is simplistic (stateless) load balancing amongst the partitions owned by the LM that "hosts" the session.
            val selectedPartition = partitions.random()
            val record = Record(LINK_IN_TOPIC, sessionId, p2pMessage)
            p2pInPublisher.publishToPartition(listOf(selectedPartition to record))
            HttpResponseStatus.OK
        }
    }

    private fun getSessionId(message: LinkInMessage): String? {
        return when (message.payload) {
            is AuthenticatedDataMessage -> (message.payload as AuthenticatedDataMessage).header.sessionId
            is AuthenticatedEncryptedDataMessage -> (message.payload as AuthenticatedEncryptedDataMessage).header.sessionId
            is InitiatorHandshakeMessage -> (message.payload as InitiatorHandshakeMessage).header.sessionId
            is ResponderHelloMessage -> (message.payload as ResponderHelloMessage).header.sessionId
            is ResponderHandshakeMessage -> (message.payload as ResponderHandshakeMessage).header.sessionId
            else -> {
                logger.warn("Invalid payload of LinkInMessage: ${message.payload::class.java}")
                return null
            }
        }
    }

    private fun generateKey(): String {
        return UUID.randomUUID().toString()
    }
}
