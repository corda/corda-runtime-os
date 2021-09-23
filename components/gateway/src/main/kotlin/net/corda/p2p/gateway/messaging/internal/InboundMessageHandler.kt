package net.corda.p2p.gateway.messaging.internal

import com.typesafe.config.ConfigFactory
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.LinkInMessage
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.InitiatorHelloMessage
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.gateway.Gateway.Companion.PUBLISHER_ID
import net.corda.p2p.gateway.Server
import net.corda.p2p.gateway.domino.LifecycleWithCoordinatorAndResources
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import net.corda.p2p.gateway.messaging.http.HttpMessage
import net.corda.p2p.gateway.messaging.session.SessionPartitionMapperImpl
import net.corda.p2p.schema.Schema.Companion.LINK_IN_TOPIC
import net.corda.v5.base.util.contextLogger
import java.io.IOException
import java.nio.ByteBuffer
import java.util.UUID

/**
 * This class implements a simple message processor for p2p messages received from other Gateways.
 */
internal class InboundMessageHandler(
    parent: LifecycleWithCoordinatorAndResources,
    private val publisherFactory: PublisherFactory,
    private val server: Server,
    private val sessionPartitionMapper: SessionPartitionMapperImpl,
) : Lifecycle, HttpEventListener,
    LifecycleWithCoordinatorAndResources(parent) {

    companion object {
        private val logger = contextLogger()
    }

    private var p2pInPublisher: Publisher? = null
    override fun resumeSequence() {
        logger.info("Starting P2P message receiver")
        val publisherConfig = PublisherConfig(PUBLISHER_ID)
        val publisher = publisherFactory.createPublisher(publisherConfig, ConfigFactory.empty())
        executeBeforePause {
            publisher.close()
        }
        p2pInPublisher = publisher
        server.addListener(this)
        executeBeforePause {
            server.removeListener(this@InboundMessageHandler)
        }

        logger.info("Started P2P message receiver")
        state = State.Up
    }

    /**
     * Handler for direct P2P messages. The payload is deserialized and then published to the ingress topic.
     * A session init request has additional handling as the Gateway needs to generate a secret and share it
     */
    override fun onMessage(message: HttpMessage) {
        if (!isRunning) {
            logger.error("Received message from ${message.source}, while handler is stopped. Discarding it and returning error code.")
            server.write(HttpResponseStatus.SERVICE_UNAVAILABLE, ByteArray(0), message.source)
            return
        }

        if (message.statusCode != HttpResponseStatus.OK) {
            logger.warn("Received invalid request from ${message.source}. Status code ${message.statusCode}")
            server.write(message.statusCode, ByteArray(0), message.source)
            return
        }

        logger.debug("Processing request message from ${message.source}")
        val p2pMessage = try {
            LinkInMessage.fromByteBuffer(ByteBuffer.wrap(message.payload))
        } catch (e: IOException) {
            logger.warn("Invalid message received. Cannot deserialize")
            logger.debug(e.stackTraceToString())
            server.write(HttpResponseStatus.INTERNAL_SERVER_ERROR, ByteArray(0), message.source)
            return
        }

        logger.debug("Received message of type ${p2pMessage.schema.name}")
        when (p2pMessage.payload) {
            is UnauthenticatedMessage -> {
                p2pInPublisher!!.publish(listOf(Record(LINK_IN_TOPIC, generateKey(), p2pMessage)))
                server.write(HttpResponseStatus.OK, ByteArray(0), message.source)
            }
            else -> {
                val statusCode = processSessionMessage(p2pMessage)
                server.write(statusCode, ByteArray(0), message.source)
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
}
