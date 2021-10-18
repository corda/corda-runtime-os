package net.corda.p2p.gateway.messaging.internal

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.InternalTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
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
import net.corda.p2p.gateway.GatewayImpl.Companion.PUBLISHER_ID
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import net.corda.p2p.gateway.messaging.http.HttpMessage
import net.corda.p2p.gateway.messaging.http.ReconfigurableHttpServer
import net.corda.p2p.gateway.messaging.session.SessionPartitionMapperImpl
import net.corda.p2p.schema.Schema.Companion.LINK_IN_TOPIC
import net.corda.v5.base.util.contextLogger
import java.nio.ByteBuffer
import java.util.UUID

/**
 * This class implements a simple message processor for p2p messages received from other Gateways.
 */
internal class InboundMessageHandler(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    configurationReaderService: ConfigurationReadService,
    publisherFactory: PublisherFactory,
    subscriptionFactory: SubscriptionFactory,
) :
    HttpEventListener,
    InternalTile(lifecycleCoordinatorFactory) {

    companion object {
        private val logger = contextLogger()
    }

    private var p2pInPublisher = PublisherWithDominoLogic(publisherFactory, lifecycleCoordinatorFactory, PUBLISHER_ID)
    private val sessionPartitionMapper = SessionPartitionMapperImpl(lifecycleCoordinatorFactory, subscriptionFactory)
    private val server = ReconfigurableHttpServer(lifecycleCoordinatorFactory, configurationReaderService, this)

    /**
     * Handler for direct P2P messages. The payload is deserialized and then published to the ingress topic.
     * A session init request has additional handling as the Gateway needs to generate a secret and share it
     */
    override fun onMessage(message: HttpMessage) {
        withLifecycleLock { handleMessage(message) }
    }
    private fun handleMessage(message: HttpMessage) {
        if (!isRunning) {
            logger.error("Received message from ${message.source}, while handler is stopped. Discarding it and returning error code.")
            server.writeResponse(HttpResponseStatus.SERVICE_UNAVAILABLE, message.source)
            return
        }

        if (message.statusCode != HttpResponseStatus.OK) {
            logger.warn("Received invalid request from ${message.source}. Status code ${message.statusCode}")
            server.writeResponse(message.statusCode, message.source)
            return
        }

        logger.debug("Processing request message from ${message.source}")
        @Suppress("TooGenericExceptionCaught")
        val p2pMessage = try {
            LinkInMessage.fromByteBuffer(ByteBuffer.wrap(message.payload))
        } catch (e: Throwable) {
            logger.warn("Invalid message received. Cannot deserialize")
            logger.debug(e.stackTraceToString())
            server.writeResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, message.source)
            return
        }

        logger.debug("Received message of type ${p2pMessage.schema.name}")
        when (p2pMessage.payload) {
            is UnauthenticatedMessage -> {
                p2pInPublisher.publish(listOf(Record(LINK_IN_TOPIC, generateKey(), p2pMessage)))
                server.writeResponse(HttpResponseStatus.OK, message.source)
            }
            else -> {
                val statusCode = processSessionMessage(p2pMessage)
                server.writeResponse(statusCode, message.source)
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
            p2pInPublisher.publishToPartition(listOf(selectedPartition to record))
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
            else -> {
                logger.warn("Invalid payload of LinkInMessage: ${message.payload::class.java}")
                return null
            }
        }
    }

    private fun generateKey(): String {
        return UUID.randomUUID().toString()
    }

    override val children: Collection<DominoTile> = listOf(sessionPartitionMapper, p2pInPublisher, server)
}
