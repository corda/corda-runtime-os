package net.corda.p2p.gateway.messaging.internal

import com.typesafe.config.Config
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.InternalTileWithResources
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.NetworkType
import net.corda.p2p.gateway.Gateway.Companion.CONSUMER_GROUP_ID
import net.corda.p2p.gateway.GatewayMessage
import net.corda.p2p.gateway.messaging.ReconfigurableConnectionManager
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpResponse
import net.corda.p2p.gateway.messaging.http.SniCalculator
import net.corda.p2p.schema.Schema
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
) : EventLogProcessor<String, LinkOutMessage>,
    Lifecycle,
    InternalTileWithResources(lifecycleCoordinatorFactory) {

    companion object {
        private val logger = LoggerFactory.getLogger(OutboundMessageHandler::class.java)
        const val MAX_RETRIES = 1
    }

    private val connectionManager = ReconfigurableConnectionManager(
        lifecycleCoordinatorFactory,
        configurationReaderService
    )

    private val p2pMessageSubscription = subscriptionFactory.createEventLogSubscription(
        SubscriptionConfig(CONSUMER_GROUP_ID, Schema.LINK_OUT_TOPIC, instanceId),
        this,
        nodeConfiguration,
        null
    )

    private val retryThreadPool = Executors.newSingleThreadScheduledExecutor()

    @Suppress("TooGenericExceptionCaught")
    override fun onNext(events: List<EventLogRecord<String, LinkOutMessage>>): List<Record<*, *>> {
        withLifecycleLock {
            if (!isRunning) {
                throw IllegalStateException("Can not handle events")
            }

            val pendingMessages = events.mapNotNull { evt ->
                evt.value?.let { peerMessage ->
                    try {
                        val sni = SniCalculator.calculateSni(
                            peerMessage.header.destinationX500Name,
                            peerMessage.header.destinationNetworkType,
                            peerMessage.header.address
                        )
                        val messageId = UUID.randomUUID().toString()
                        val gatewayMessage = GatewayMessage(messageId, peerMessage.payload)
                        val message = gatewayMessage.toByteBuffer().array()
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
                        val responseFuture = connectionManager.acquire(destinationInfo).write(message)
                        PendingRequest(gatewayMessage, destinationInfo, responseFuture)
                    } catch (e: IllegalArgumentException) {
                        logger.warn("Can't send message to destination ${peerMessage.header.address}. ${e.message}")
                        null
                    }
                }
            }

            waitUntilComplete(pendingMessages)
            pendingMessages.forEach { pendingMessage ->
                val (response, error) = try {
                    val response = pendingMessage.future.get(0, TimeUnit.MILLISECONDS)
                    response to null
                } catch (error: Exception) {
                    null to error
                }
                handleResponse(pendingMessage, response, error, MAX_RETRIES)
            }

        }
        return emptyList()
    }

    @Suppress("TooGenericExceptionCaught", "SpreadOperator")
    private fun waitUntilComplete(pendingRequests: List<PendingRequest>) {
        try {
            CompletableFuture.allOf( *pendingRequests.map{ it.future }.toTypedArray() )
                .get(connectionManager.latestConnectionConfig().responseTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            // Do nothing - results/errors will be processed individually.
        }
    }

    private fun scheduleMessageReplay(destinationInfo: DestinationInfo, gatewayMessage: GatewayMessage, remainingAttempts: Int) {
        retryThreadPool.schedule({
            val future = connectionManager.acquire(destinationInfo).write(gatewayMessage.toByteBuffer().array())
            val pendingRequest = PendingRequest(gatewayMessage, destinationInfo, future)
            future.orTimeout(connectionManager.latestConnectionConfig().responseTimeout.toMillis(), TimeUnit.MILLISECONDS)
                  .whenCompleteAsync { response, error ->
                        handleResponse(pendingRequest, response, error, remainingAttempts - 1)
                  }
        }, connectionManager.latestConnectionConfig().retryDelay.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun handleResponse(pendingRequest: PendingRequest, response: HttpResponse?, error: Throwable?, remainingAttempts: Int) {
        if (error != null) {
            if (remainingAttempts > 0) {
                logger.warn("Request (${pendingRequest.gatewayMessage.id}) failed, it will be retried later.", error)
                scheduleMessageReplay(pendingRequest.destinationInfo, pendingRequest.gatewayMessage, remainingAttempts)
            } else {
                logger.warn("Request (${pendingRequest.gatewayMessage.id}) failed.", error)
            }
        } else if (response != null) {
            if (response.statusCode != HttpResponseStatus.OK) {
                if (shouldRetry(response.statusCode) && remainingAttempts > 0) {
                    logger.warn("Request (${pendingRequest.gatewayMessage.id}) failed with status code ${response.statusCode}, " +
                            "it will be retried later.")
                    scheduleMessageReplay(pendingRequest.destinationInfo, pendingRequest.gatewayMessage, remainingAttempts)
                } else {
                    logger.warn("Request (${pendingRequest.gatewayMessage.id}) failed with status code ${response.statusCode}.")
                }
            }
        }
    }

    private fun shouldRetry(statusCode: HttpResponseStatus): Boolean {
        return statusCode.code() >= 500
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<LinkOutMessage>
        get() = LinkOutMessage::class.java

    override val children = listOf(connectionManager)
    override fun createResources() {
        resources.keep(p2pMessageSubscription::stop)
        p2pMessageSubscription.start()
    }

    private data class PendingRequest(val gatewayMessage: GatewayMessage,
                                      val destinationInfo: DestinationInfo,
                                      val future: CompletableFuture<HttpResponse>)
}
