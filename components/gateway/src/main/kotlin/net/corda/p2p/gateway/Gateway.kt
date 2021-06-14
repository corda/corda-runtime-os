package net.corda.p2p.gateway

import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.LifeCycle
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.gateway.messaging.ConnectionManager
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.p2p.gateway.messaging.internal.OutboundMessageHandler
import net.corda.v5.base.util.NetworkHostAndPort
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.util.concurrent.CountDownLatch

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
//              kafkaBrokers: List<NetworkHostAndPort>,
//              partitionNumber: Int = 1,
              sslConfig: SslConfiguration,
//              subscriptionFactory: SubscriptionFactory, // Inject manually for testing until OSGIfication happens
//              publisherFactory: PublisherFactory // Inject manually for testing unti OSGIfication happens
) : LifeCycle {
    private val logger = LoggerFactory.getLogger(Gateway::class.java)

    private val shutdownListener = CountDownLatch(1)
    private val closeActions = mutableListOf<() -> Unit>()
    private val httpServer = HttpServer(address, sslConfig)
    private val connectionManager = ConnectionManager(sslConfig)
    private val messageProcessor = OutboundMessageHandler()

    override fun start() {
        logger.info("Starting Gateway service")
        closeActions += { shutdownListener.countDown() }
        httpServer.start()
        closeActions += { httpServer.close() }
        connectionManager.start()
        closeActions += { connectionManager.close() }
        messageProcessor.start()
        closeActions += { messageProcessor.close() }
        logger.info("Gateway started")
        shutdownListener.await()
    }

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