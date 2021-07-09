package net.corda.p2p.gateway

import com.typesafe.config.ConfigFactory
import net.corda.lifecycle.LifeCycle
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.gateway.messaging.ConnectionManager
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.p2p.gateway.messaging.internal.InboundMessageHandler
import net.corda.p2p.gateway.messaging.internal.OutboundMessageHandler
import net.corda.p2p.gateway.messaging.internal.PartitionAssignmentListenerImpl
import net.corda.v5.base.util.NetworkHostAndPort
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.lang.Exception

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
 *
 */
class Gateway(address: NetworkHostAndPort,
              sslConfig: SslConfiguration,
              @Reference(service = SubscriptionFactory::class)
              subscriptionFactory: SubscriptionFactory,
              @Reference(service = PublisherFactory::class)
              publisherFactory: PublisherFactory
) : LifeCycle {

    companion object {
        /**
         * Topic names used to communicate with upstream services, specifically Link Manager
         *
         */
        const val P2P_IN_TOPIC = "p2p.in"
        const val P2P_OUT_TOPIC = "p2p.out"
        const val CONSUMER_GROUP_ID = "gateway"
        const val PUBLISHER_ID = "gateway"

        /**
         * Temporary value used to negotiate P2P sessions. Will be removed when design changes
         */
        const val MAX_MESSAGE_SIZE = 1024 * 1024
    }

    private val logger = LoggerFactory.getLogger(Gateway::class.java)

    private val closeActions = mutableListOf<() -> Unit>()
    private val httpServer = HttpServer(address, sslConfig)
    private val connectionManager = ConnectionManager(sslConfig)
    private var p2pMessageSubscription: Subscription<String, LinkOutMessage>
    private val inboundMessageProcessor = InboundMessageHandler(httpServer, connectionManager, publisherFactory)

    private var started = false
    override val isRunning: Boolean
        get() = started

    init {
        val subscriptionConfig = SubscriptionConfig(CONSUMER_GROUP_ID, P2P_OUT_TOPIC)
        p2pMessageSubscription = subscriptionFactory.createEventLogSubscription(subscriptionConfig,
            OutboundMessageHandler(connectionManager),
            ConfigFactory.empty(),
            PartitionAssignmentListenerImpl())
    }

    override fun start() {
        logger.info("Starting Gateway service")
        connectionManager.start()
        closeActions += { connectionManager.close() }
        httpServer.start()
        closeActions += { httpServer.close() }
        inboundMessageProcessor.start()
        closeActions += { inboundMessageProcessor.close() }
        p2pMessageSubscription.start()
        closeActions += { p2pMessageSubscription.close() }
        logger.info("Gateway started")
    }

    @Suppress("TooGenericExceptionCaught")
    override fun stop() {
        logger.info("Shutting down")
        for (closeAction in closeActions.reversed()) {
            try {
                closeAction()
            } catch (e: InterruptedException) {
                logger.warn("InterruptedException was thrown during shutdown, ignoring.")
            } catch (e: Exception) {
                logger.warn("Exception thrown during shutdown.", e)
            }
        }

        logger.info("Shutdown complete")
    }
}