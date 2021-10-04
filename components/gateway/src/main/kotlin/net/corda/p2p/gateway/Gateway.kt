package net.corda.p2p.gateway

import net.corda.configuration.read.ConfigurationReadService
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.gateway.domino.DominoTile
import net.corda.p2p.gateway.domino.OrphanDominoTile
import net.corda.p2p.gateway.messaging.internal.InboundMessageHandler
import net.corda.p2p.gateway.messaging.internal.OutboundMessageHandler
import org.osgi.service.component.annotations.Reference

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
// YIFT: should this be internal?
class Gateway(
    @Reference(service = ConfigurationReadService::class)
    configurationReaderService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    publisherFactory: PublisherFactory,
) : OrphanDominoTile() {

    companion object {
        const val CONSUMER_GROUP_ID = "gateway"
        const val PUBLISHER_ID = "gateway"
    }

    private val inboundMessageHandler = InboundMessageHandler(
        this,
        configurationReaderService,
        publisherFactory,
        subscriptionFactory,
    )
    private val outboundMessageProcessor = OutboundMessageHandler(
        this,
        configurationReaderService,
        subscriptionFactory,
        publisherFactory,
    )

    override val children: Collection<DominoTile> = listOf(inboundMessageHandler, outboundMessageProcessor)

    override fun startSequence() {
        state = State.Running
    }
}
