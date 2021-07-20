package net.corda.p2p.gateway.messaging.internal

import com.typesafe.config.ConfigFactory
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.lifecycle.LifeCycle
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.LinkInMessage
import net.corda.p2p.Step2Message
import net.corda.p2p.crypto.InitiatorHelloMessage
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.gateway.Gateway.Companion.PUBLISHER_ID
import net.corda.p2p.gateway.messaging.http.HttpMessage
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.p2p.schema.Schema.Companion.LINK_IN_TOPIC
import org.slf4j.LoggerFactory
import rx.Subscription
import java.io.IOException
import java.nio.ByteBuffer

/**
 * This class implements a simple message processor for p2p messages received from other Gateways.
 */
class InboundMessageHandler(private val server: HttpServer,
                            private val maxMessageSize: Int,
                            private val publisherFactory: PublisherFactory) : LifeCycle {

    companion object {
        private var logger = LoggerFactory.getLogger(InboundMessageHandler::class.java)
    }

    private var inboundMessageListener: Subscription? = null
    private var p2pInPublisher: Publisher? = null
    private var connectionListener: Subscription? = null

    private var started = false
    override val isRunning: Boolean
        get() = started

    override fun start() {
        logger.info("Starting P2P message receiver")
        val publisherConfig = PublisherConfig(PUBLISHER_ID)
        p2pInPublisher = publisherFactory.createPublisher(publisherConfig, ConfigFactory.empty())
        inboundMessageListener = server.onReceive.subscribe { handleRequestMessage(it) }
        started = true
        logger.info("Started P2P message receiver")
    }

    override fun stop() {
        started = false
        inboundMessageListener?.unsubscribe()
        inboundMessageListener = null
        connectionListener?.unsubscribe()
        connectionListener = null
        p2pInPublisher?.close()
        p2pInPublisher = null
    }

    /**
     * Handler for direct P2P messages. The payload is deserialized and then published to the ingress topic.
     * A session init request has additional handling as the Gateway needs to generate a secret and share it
     */
    private fun handleRequestMessage(message: HttpMessage) {
        var responseBytes = ByteArray(0)
        var statusCode = message.statusCode
        try {
            logger.debug("Processing request message from ${message.source}")
            val p2pMessage = LinkInMessage.fromByteBuffer(ByteBuffer.wrap(message.payload))
            val record = when (p2pMessage.payload) {
                is InitiatorHelloMessage -> {
                    // Generate a response containing the server hello and the DH secret
                    val sessionRequest = p2pMessage.payload as InitiatorHelloMessage
                    val session = AuthenticationProtocolResponder(sessionRequest.header.sessionId,
                        sessionRequest.supportedModes.toSet(), maxMessageSize)
                    session.receiveInitiatorHello(sessionRequest)
                    val sessionInitResponse = session.generateResponderHello()
                    val p2pOutMessage = LinkInMessage(sessionInitResponse)
                    responseBytes = p2pOutMessage.toByteBuffer().array()
                    val (pKey, _) = session.getDHKeyPair()
                    val step2Message = Step2Message.newBuilder().apply {
                        initiatorHello = sessionRequest
                        responderHello = sessionInitResponse
                        privateKey = ByteBuffer.wrap(pKey)
                    }
                    Record(LINK_IN_TOPIC, "key", LinkInMessage(step2Message))
                }
                else -> {
                    Record(LINK_IN_TOPIC, "key", p2pMessage)
                }
            }
            logger.debug("Received message of type ${p2pMessage.schema.name}")
            p2pInPublisher?.publish(listOf(record))
        } catch (e: IOException) {
            logger.warn("Invalid message received. Cannot deserialize")
            logger.debug(e.stackTraceToString())
            statusCode = HttpResponseStatus.INTERNAL_SERVER_ERROR
        } finally {
            server.write(statusCode, responseBytes, message.source)
        }
    }
}