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
import net.corda.p2p.gateway.Gateway.Companion.MAX_MESSAGE_SIZE
import net.corda.p2p.gateway.Gateway.Companion.P2P_IN_TOPIC
import net.corda.p2p.gateway.Gateway.Companion.PUBLISHER_ID
import net.corda.p2p.gateway.messaging.ConnectionManager
import net.corda.p2p.gateway.messaging.ReceivedMessage
import net.corda.p2p.gateway.messaging.ResponseMessage
import net.corda.p2p.gateway.messaging.http.HttpServer
import org.slf4j.LoggerFactory
import rx.Subscription
import java.io.IOException
import java.nio.ByteBuffer

/**
 * This class implements a simple message processor for p2p messages received from other Gateways.
 */
class InboundMessageHandler(private val server: HttpServer,
//                            private val connectionManager: ConnectionManager,
                            private val publisherFactory: PublisherFactory) : LifeCycle {

    private var logger = LoggerFactory.getLogger(InboundMessageHandler::class.java)
    private var inboundMessageListener: Subscription? = null
    private var p2pInPublisher: Publisher? = null
//    private val clientMessageHandlers = mutableListOf<Subscription>()
    private var connectionListener: Subscription? = null

    private var started = false
    override val isRunning: Boolean
        get() = started

    override fun start() {
        logger.info("Starting P2P message receiver")
        val publisherConfig = PublisherConfig(PUBLISHER_ID)
        p2pInPublisher = publisherFactory.createPublisher(publisherConfig, ConfigFactory.empty())
        inboundMessageListener = server.onReceive.subscribe { handleRequestMessage(it) }
//        connectionListener = connectionManager.onNewConnection.subscribe { clientObs ->
//            val clientMessageReceiver = clientObs.subscribe { responseMessageHandler(it) }
//            clientMessageHandlers.add(clientMessageReceiver)
//        }
        started = true
        logger.info("Started P2P message receiver")
    }

    override fun stop() {
        started = false
        inboundMessageListener?.unsubscribe()
        inboundMessageListener = null
//        clientMessageHandlers.forEach { it.unsubscribe() }
        connectionListener?.unsubscribe()
        connectionListener = null
        p2pInPublisher?.close()
        p2pInPublisher = null
    }

    /**
     * Handler for direct P2P messages. The payload is deserialized and then published to the ingress topic.
     * A session init request has additional handling as the Gateway needs to generate a secret and share it
     */
    private fun handleRequestMessage(message: ReceivedMessage) {
        var responseBytes = ByteArray(0)
        var statusCode = message.response.status()
        try {
            logger.info("Processing request message from ${message.source}")
            val p2pMessage = LinkInMessage.fromByteBuffer(ByteBuffer.wrap(message.payload))
            val record = when (p2pMessage.payload) {
                is InitiatorHelloMessage -> {
                    // Generate a response containing the server hello and the DH secret
                    val sessionRequest = p2pMessage.payload as InitiatorHelloMessage
                    val session = AuthenticationProtocolResponder(sessionRequest.header.sessionId,
                        sessionRequest.supportedModes.toSet(),
                        MAX_MESSAGE_SIZE)
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
                    Record(P2P_IN_TOPIC, "key", LinkInMessage(step2Message))
                }
                else -> {
                    Record(P2P_IN_TOPIC, "key", p2pMessage)
                }
            }
            logger.info("Received message of type ${p2pMessage.schema.name}")
            p2pInPublisher?.publish(listOf(record))
        } catch (e: IOException) {
            logger.warn("Invalid message received. Cannot deserialize")
            logger.debug(e.stackTraceToString())
            statusCode = HttpResponseStatus.INTERNAL_SERVER_ERROR
        } finally {
            server.write(statusCode, responseBytes, message.source)
            message.release()
        }
    }

//    /**
//     * Handler for P2P messages sent back as a result of a request. Typically, these responses have no payloads and serve
//     * as an indication of successful receipt on the other end. In case of a session request message, the response will
//     * contain information which then needs to be forwarded to the LinkManager
//     */
//    private fun responseMessageHandler(message: ResponseMessage) {
//        logger.info("Processing response message from ${message.source} with status $${message.statusCode}")
//        if (HttpResponseStatus.OK == message.statusCode) {
//            // response messages should have empty payloads unless they are part of the initial session handshake
//            if (message.payload.isNotEmpty()) {
//                try {
//                    // Attempt to deserialize as an early check. Shouldn't forward unrecognised message types
//                    LinkInMessage.fromByteBuffer(ByteBuffer.wrap(message.payload))
//                    val record = Record(P2P_IN_TOPIC, "key", message)
//                    p2pInPublisher?.publish(listOf(record))
//                } catch (e: IOException) {
//                    logger.warn("Invalid message received. Cannot deserialize")
//                    logger.debug(e.stackTraceToString())
//                }
//            }
//        } else {
//            logger.warn("Something went wrong with peer processing an outbound message. Peer response status ${message.statusCode}")
//        }
//
//        message.release()
//    }

}