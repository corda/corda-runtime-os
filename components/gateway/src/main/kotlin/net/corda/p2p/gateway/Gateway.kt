package net.corda.p2p.gateway

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.gateway.domino.LifecycleWithCoordinatorAndResources
import net.corda.p2p.gateway.messaging.internal.InboundMessageHandler
import net.corda.p2p.gateway.messaging.internal.OutboundMessageHandler
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

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
    "${instanceId.incrementAndGet()}"
) {

    companion object {
        private val logger = LoggerFactory.getLogger(Gateway::class.java)
        const val CONSUMER_GROUP_ID = "gateway"
        const val PUBLISHER_ID = "gateway"

        private val instanceId = AtomicInteger(0)
    }

    private val configurationService = GatewayConfigurationService(this, configurationReaderService)

    private val inboundMessageHandler = InboundMessageHandler(
        this,
        configurationService,
        publisherFactory,
        subscriptionFactory,
    )
    private val outboundMessageProcessor = OutboundMessageHandler(
        this,
        configurationService,
        subscriptionFactory,
        publisherFactory,
    )

    override fun openSequence() {
        followStatusChanges(inboundMessageHandler, outboundMessageProcessor).also {
            executeBeforeClose(it::close)
        }
    }

    override fun resumeSequence() {
        logger.info("Starting Gateway service")
        configurationService.start()
        inboundMessageHandler.start()
        executeBeforePause(inboundMessageHandler::stop)
        outboundMessageProcessor.start()
        executeBeforePause(outboundMessageProcessor::stop)
        onStatusUp()
        logger.info("Gateway started")
    }

    override fun onStatusUp() {
        if ((configurationService.state == State.Up) &&
            (inboundMessageHandler.state == State.Up) &&
            (outboundMessageProcessor.state == State.Up)
        ) {
            logger.info("Gateway is running")
            state = State.Up
        }
    }

    override fun onStatusDown() {
        outboundMessageProcessor.stop()
        inboundMessageHandler.stop()
    }
}
