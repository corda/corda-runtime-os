package net.corda.p2p.gateway

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.InternalTile
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.gateway.messaging.internal.InboundMessageHandler
import net.corda.p2p.gateway.messaging.internal.OutboundMessageHandler

/**
 * The Gateway is a light component which facilitates the sending and receiving of P2P messages.
 * Upon connecting to the internal messaging system, the Gateway will subscribe to the different topics for outgoing messages.
 * Each such message will trigger the creation or retrieval of a persistent HTTP connection to the target (specified in the
 * message header).
 *
 * The messaging relies on shallow POST requests, meaning the serving Gateway will send a response back immediately after
 * receipt of the request. Once e response arrives, it is inspected for any server side errors and, if needed, published
 * to the internal messaging system.
 *
 */
@Suppress("LongParameterList")
class Gateway(
    configurationReaderService: ConfigurationReadService,
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    nodeConfiguration: SmartConfig,
    instanceId: Int,
) : InternalTile(
    lifecycleCoordinatorFactory,
) {

    companion object {
        const val CONSUMER_GROUP_ID = "gateway"
        const val PUBLISHER_ID = "gateway"
        const val CONFIG_KEY = "p2p.gateway"
    }

    private val inboundMessageHandler = InboundMessageHandler(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        publisherFactory,
        subscriptionFactory,
        nodeConfiguration,
    )
    private val outboundMessageProcessor = OutboundMessageHandler(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        subscriptionFactory,
        nodeConfiguration,
        instanceId,
    )

    override val children: Collection<DominoTile> = listOf(inboundMessageHandler, outboundMessageProcessor)
}
