package net.corda.p2p.gateway.messaging.internal

import com.typesafe.config.Config
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.NetworkType
import net.corda.p2p.gateway.Gateway.Companion.CONSUMER_GROUP_ID
import net.corda.p2p.gateway.messaging.ReconfigurableConnectionManager
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import net.corda.p2p.gateway.messaging.http.HttpMessage
import net.corda.p2p.gateway.messaging.http.SniCalculator
import net.corda.p2p.schema.Schema
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * This is an implementation of an [EventLogProcessor] used to consume messages from a P2P message subscription. The received
 * events are processed and fed into the HTTP pipeline. No records will be produced by this processor as a result.
 */
internal class OutboundMessageHandler(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    configurationReaderService: ConfigurationReadService,
    subscriptionFactory: SubscriptionFactory,
    nodeConfiguration: Config,
    instanceId: Int,

) : EventLogProcessor<String, LinkOutMessage>, LifecycleWithDominoTile, HttpEventListener {
    companion object {
        private val logger = LoggerFactory.getLogger(OutboundMessageHandler::class.java)
    }

    private val connectionManager = ReconfigurableConnectionManager(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        this,
    )

    override val dominoTile = DominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        children = listOf(connectionManager.dominoTile),
        createResources = ::createResources
    )

    private val p2pMessageSubscription = subscriptionFactory.createEventLogSubscription(
        SubscriptionConfig(CONSUMER_GROUP_ID, Schema.LINK_OUT_TOPIC, instanceId),
        this,
        nodeConfiguration,
        null
    )

    @Suppress("NestedBlockDepth")
    override fun onNext(events: List<EventLogRecord<String, LinkOutMessage>>): List<Record<*, *>> {
        dominoTile.withLifecycleLock {
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

    override fun onMessage(message: HttpMessage) {
        logger.debug("Processing response message from ${message.source} with status ${message.statusCode}")
        if (message.statusCode != HttpResponseStatus.OK) {
            logger.warn("Something went wrong with peer processing an outbound message. Peer response status ${message.statusCode}")
        }
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<LinkOutMessage>
        get() = LinkOutMessage::class.java


    private fun createResources(resources: ResourcesHolder) {
        resources.keep(p2pMessageSubscription)
        p2pMessageSubscription.start()
        dominoTile.resourcesStarted(false)
    }
}
