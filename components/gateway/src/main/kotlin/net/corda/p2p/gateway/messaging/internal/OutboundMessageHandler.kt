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
import net.corda.p2p.gateway.GatewayResponse
import net.corda.p2p.gateway.messaging.ReconfigurableConnectionManager
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import net.corda.p2p.gateway.messaging.http.HttpMessage
import net.corda.p2p.gateway.messaging.http.SniCalculator
import net.corda.p2p.schema.Schema
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
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
    HttpEventListener,
    InternalTileWithResources(lifecycleCoordinatorFactory) {
    companion object {
        private val logger = LoggerFactory.getLogger(OutboundMessageHandler::class.java)
        const val MAX_RETRIES = 1
    }

    private val connectionManager = ReconfigurableConnectionManager(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        this,
    )

    private val p2pMessageSubscription = subscriptionFactory.createEventLogSubscription(
        SubscriptionConfig(CONSUMER_GROUP_ID, Schema.LINK_OUT_TOPIC, instanceId),
        this,
        nodeConfiguration,
        null
    )

    private val pendingRequestFutures = ConcurrentHashMap<String, PendingRequest>()
    private val retryThreadPool = Executors.newSingleThreadScheduledExecutor()

    @Suppress("NestedBlockDepth")
    override fun onNext(events: List<EventLogRecord<String, LinkOutMessage>>): List<Record<*, *>> {
        withLifecycleLock {
            if (!isRunning) {
                throw IllegalStateException("Can not handle events")
            }

            val pendingRequests = events.mapNotNull { evt ->
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
                        connectionManager.acquire(destinationInfo).write(message)
                        val pendingRequest = PendingRequest(gatewayMessage, destinationInfo, CompletableFuture())
                        pendingRequestFutures[messageId] = pendingRequest
                        pendingRequest
                    } catch (e: IllegalArgumentException) {
                        logger.warn("Can't send message to destination ${peerMessage.header.address}. ${e.message}")
                        null
                    }
                }
            }

            pendingRequests.forEach { (gatewayMessage, destinationInfo, future) ->
                try {
                    future.get(connectionManager.latestConnectionConfig.responseTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    pendingRequestFutures.remove(gatewayMessage.id)
                } catch (e: Exception) {
                    logger.warn("Request (${gatewayMessage.id}) failed, it will be retried later.", e)
                    pendingRequestFutures.remove(gatewayMessage.id)
                    scheduleMessageReplay(destinationInfo, gatewayMessage, MAX_RETRIES)
                }
            }

        }
        return emptyList()
    }

    private fun scheduleMessageReplay(destinationInfo: DestinationInfo, gatewayMessage: GatewayMessage, remainingAttempts: Int) {
        if (remainingAttempts > 0) {
            retryThreadPool.schedule({
                connectionManager.acquire(destinationInfo).write(gatewayMessage.toByteBuffer().array())
                val pendingRequest = PendingRequest(gatewayMessage, destinationInfo, CompletableFuture())
                pendingRequestFutures[gatewayMessage.id] = pendingRequest
                pendingRequest.future.whenCompleteAsync { _, error ->
                    pendingRequestFutures.remove(gatewayMessage.id)
                    if (error != null) {
                        scheduleMessageReplay(destinationInfo, gatewayMessage, remainingAttempts - 1)
                    }
                }
            }, connectionManager.latestConnectionConfig.retryDelay.toMillis(), TimeUnit.MILLISECONDS)
        }
    }

    override fun onMessage(message: HttpMessage) {
        logger.debug("Processing response message from ${message.source} with status ${message.statusCode}")
        when(message.statusCode) {
            HttpResponseStatus.OK -> {
                val gatewayResponse = GatewayResponse.fromByteBuffer(ByteBuffer.wrap(message.payload))
                pendingRequestFutures[gatewayResponse.id]?.future?.complete(Unit)
            }
            HttpResponseStatus.SERVICE_UNAVAILABLE, HttpResponseStatus.BAD_REQUEST -> {
                logger.warn("Destination at (${message.source}) failed to process message with ${message.statusCode.code()}")
            }
            else -> {
                if (message.payload.isNotEmpty()) {
                    val gatewayResponse = GatewayResponse.fromByteBuffer(ByteBuffer.wrap(message.payload))
                    val error = RuntimeException("Destination at (${message.source}) failed to process message. " +
                            "Response status: ${message.statusCode}")
                    pendingRequestFutures[gatewayResponse.id]?.future?.completeExceptionally(error)
                } else {
                    logger.warn("Destination at (${message.source}) failed to process message. " +
                            "Response status: ${message.statusCode}")
                }
            }
        }
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

    private data class PendingRequest(val gatewayMessage: GatewayMessage, val destinationInfo: DestinationInfo, val future: CompletableFuture<Unit>)
}
