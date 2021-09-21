package net.corda.p2p.gateway

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.gateway.domino.LifecycleWithCoordinatorAndResources
import net.corda.p2p.gateway.messaging.internal.OutboundMessageHandler
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
) : LifecycleWithCoordinatorAndResources(
    lifecycleCoordinatorFactory,
    UUID.randomUUID().toString().replace("-", "")
) {

    companion object {
        private val logger = LoggerFactory.getLogger(Gateway::class.java)
        const val CONSUMER_GROUP_ID = "gateway"
        const val PUBLISHER_ID = "gateway"
    }

    private val configurationService = GatewayConfigurationService(this, configurationReaderService)

    private val server = Server(
        this,
        configurationService,
        subscriptionFactory,
        publisherFactory
    )
    private val outboundMessageProcessor = OutboundMessageHandler(
        this,
        configurationService,
        subscriptionFactory,
        publisherFactory,
    )

    init {
        followStatusChanges(server).also {
            executeBeforeClose(it::close)
        }
        followStatusChanges(outboundMessageProcessor).also {
            executeBeforeClose(it::close)
        }
    }

    override fun onStart() {
        logger.info("Starting Gateway service")
        configurationService.start()
        server.start()
        executeBeforeStop(server::stop)
        onStatusChange(LifecycleStatus.UP)
        outboundMessageProcessor.start()
        executeBeforeStop(outboundMessageProcessor::stop)
        logger.info("Gateway started")
    }

    override fun onStatusChange(newStatus: LifecycleStatus) {
        if (newStatus == LifecycleStatus.UP) {
            if ((configurationService.status == LifecycleStatus.UP) &&
                (server.status == LifecycleStatus.UP) &&
                (outboundMessageProcessor.status == LifecycleStatus.UP)
            ) {
                logger.info("Gateway is running")
                status = LifecycleStatus.UP
            }
        } else {
            outboundMessageProcessor.stop()
            server.stop()
        }
        super.onStatusChange(newStatus)
    }

    // YIFT: Remove?
    fun stopAndWaitForDestruction() {
        stop()
        while (status != LifecycleStatus.DOWN) {
            Thread.sleep(100)
        }
    }
}
