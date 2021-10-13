package net.corda.p2p.gateway.messaging.internal

import com.typesafe.config.ConfigFactory
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.InternalTileWithResources
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.NetworkType
import net.corda.p2p.gateway.Gateway
import net.corda.p2p.gateway.Gateway.Companion.PUBLISHER_ID
import net.corda.p2p.gateway.messaging.ReconfigurableConnectionManager
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import net.corda.p2p.gateway.messaging.http.HttpMessage
import net.corda.p2p.gateway.messaging.http.SniCalculator
import net.corda.p2p.schema.Schema
import net.corda.p2p.schema.Schema.Companion.LINK_IN_TOPIC
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.ByteBuffer

/**
 * This is an implementation of an [EventLogProcessor] used to consume messages from a P2P message subscription. The received
 * events are processed and fed into the HTTP pipeline. No records will be produced by this processor as a result.
 */
internal class OutboundMessageHandler(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    configurationReaderService: ConfigurationReadService,
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
) : EventLogProcessor<String, LinkOutMessage>,
    Lifecycle,
    HttpEventListener,
    InternalTileWithResources(lifecycleCoordinatorFactory) {
    companion object {
        private val logger = LoggerFactory.getLogger(OutboundMessageHandler::class.java)
    }

    private val connectionManager = ReconfigurableConnectionManager(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        this,
    )

    private var p2pInPublisher = PublisherWithDominoLogic(
        publisherFactory,
        lifecycleCoordinatorFactory,
        PUBLISHER_ID
    )

    private val p2pMessageSubscription = subscriptionFactory.createEventLogSubscription(
        SubscriptionConfig(Gateway.CONSUMER_GROUP_ID, Schema.LINK_OUT_TOPIC),
        this,
        ConfigFactory.empty(),
        null
    )

    @Suppress("NestedBlockDepth")
    override fun onNext(events: List<EventLogRecord<String, LinkOutMessage>>): List<Record<*, *>> {
        withLifecycleLock {
            if (!isRunning) {
                throw IllegalStateException("Can not handle events")
            }

            events.forEach { evt ->
                evt.value?.let { peerMessage ->
                    try {
                        val sni = SniCalculator.calculateSni(
                            peerMessage.header.destinationX500Name,
                            peerMessage.header.destinationNetworkType,
                            peerMessage.header.address
                        )
                        val message = LinkInMessage(peerMessage.payload).toByteBuffer().array()
                        val expectedX500Name = if (NetworkType.CORDA_4 == peerMessage.header.destinationNetworkType) {
                            X500Name(peerMessage.header.destinationX500Name)
                        } else {
                            null
                        }
                        val destinationInfo = DestinationInfo(
                            URI.create(peerMessage.header.address),
                            sni,
                            expectedX500Name
                        )
                        connectionManager.acquire(destinationInfo).write(message)
                    } catch (e: IllegalArgumentException) {
                        logger.warn("Can't send message to destination ${peerMessage.header.address}. ${e.message}")
                    }
                }
            }
        }
        return emptyList()
    }

    /**
     * Handler for P2P messages sent back as a result of a request. Typically, these responses have no payloads and serve
     * as an indication of successful receipt on the other end. In case of a session request message, the response will
     * contain information which then needs to be forwarded to the LinkManager
     */
    override fun onMessage(message: HttpMessage) {
        logger.debug("Processing response message from ${message.source} with status ${message.statusCode}")
        withLifecycleLock {
            if (!isRunning) {
                logger.warn("Can not handle message")
                return@withLifecycleLock
            }
            if (HttpResponseStatus.OK == message.statusCode) {
                // response messages should have empty payloads unless they are part of the initial session handshake
                if (message.payload.isNotEmpty()) {
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        // Attempt to deserialize as an early check. Shouldn't forward unrecognised message types
                        LinkInMessage.fromByteBuffer(ByteBuffer.wrap(message.payload))
                        val record = Record(LINK_IN_TOPIC, "key", message)
                        p2pInPublisher.publish(listOf(record))
                    } catch (e: Throwable) {
                        logger.warn("Invalid message received. Cannot deserialize")
                        logger.debug(e.stackTraceToString())
                    }
                }
            } else {
                logger.warn("Something went wrong with peer processing an outbound message. Peer response status ${message.statusCode}")
            }
        }
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<LinkOutMessage>
        get() = LinkOutMessage::class.java

    override val children = listOf(connectionManager, p2pInPublisher)
    override fun createResources() {
        resources.keep(p2pMessageSubscription::stop)
        p2pMessageSubscription.start()
    }
}
