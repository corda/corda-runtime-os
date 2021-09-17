package net.corda.p2p.gateway

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.gateway.domino.DominoCoordinatorFactory
import net.corda.p2p.gateway.domino.DominoTile
import net.corda.p2p.gateway.messaging.ConnectionManager
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.p2p.gateway.messaging.internal.InboundMessageHandler
import net.corda.p2p.gateway.messaging.internal.OutboundMessageHandler
import net.corda.p2p.gateway.messaging.session.SessionPartitionMapperImpl
import net.corda.p2p.schema.Schema.Companion.LINK_OUT_TOPIC
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.UUID

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
    @Reference(service = LifecycleCoordinatorFactory::class)
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
) : DominoTile(
    DominoCoordinatorFactory(
        lifecycleCoordinatorFactory,
        UUID.randomUUID().toString().replace("-", "")
    )
) {

    companion object {
        private val logger = LoggerFactory.getLogger(Gateway::class.java)
        const val CONSUMER_GROUP_ID = "gateway"
        const val PUBLISHER_ID = "gateway"
    }

    private val configurationListener = GatewayConfigurationListener(
        configurationReaderService,
        coordinatorFactory
    )

    private val httpServer = HttpServer(
        coordinatorFactory,
        configurationListener
    )
    private val connectionManager = ConnectionManager(coordinatorFactory, configurationListener)
    private val sessionPartitionMapper = SessionPartitionMapperImpl(coordinatorFactory, subscriptionFactory)
    private val inboundMessageProcessor = InboundMessageHandler(
        coordinatorFactory,
        httpServer,
        publisherFactory,
        sessionPartitionMapper
    )
    private val outboundMessageProcessor = OutboundMessageHandler(
        coordinatorFactory, connectionManager, publisherFactory
    )
    private val p2pMessageSubscription = subscriptionFactory.createEventLogSubscription(
        SubscriptionConfig(CONSUMER_GROUP_ID, LINK_OUT_TOPIC),
        outboundMessageProcessor,
        ConfigFactory.empty(),
        null
    )

    override fun prepareResources() {
        logger.info("Starting Gateway service")
        keepResources(
            configurationListener,
            connectionManager,
            httpServer,
            sessionPartitionMapper,
            inboundMessageProcessor,
            outboundMessageProcessor,
            p2pMessageSubscription
        )
        logger.info("Gateway started")
    }
}
