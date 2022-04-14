package net.corda.p2p.gateway.messaging.internal

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.p2p.gateway.GatewayMessage
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.NetworkType
import net.corda.p2p.gateway.messaging.ReconfigurableConnectionManager
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpResponse
import net.corda.p2p.gateway.messaging.http.SniCalculator
import net.corda.p2p.gateway.messaging.http.TrustStoresMap
import net.corda.schema.Schemas.P2P.Companion.LINK_OUT_TOPIC
import net.corda.v5.base.util.debug
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * This is an implementation of an [PubSubProcessor] used to consume messages from a P2P message subscription. The received
 * events are processed and fed into the HTTP pipeline. No records will be produced by this processor as a result.
 */
@Suppress("LongParameterList")
internal class OutboundMessageHandler(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    configurationReaderService: ConfigurationReadService,
    subscriptionFactory: SubscriptionFactory,
    nodeConfiguration: SmartConfig,
    private val threadPool: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
) : PubSubProcessor<String, LinkOutMessage>, LifecycleWithDominoTile {

    companion object {
        private val logger = LoggerFactory.getLogger(OutboundMessageHandler::class.java)
        const val MAX_RETRIES = 1
    }

    private val connectionConfigReader = ConnectionConfigReader(lifecycleCoordinatorFactory, configurationReaderService)

    private val connectionManager = ReconfigurableConnectionManager(
        lifecycleCoordinatorFactory,
        configurationReaderService
    )

    private val trustStoresMap = TrustStoresMap(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        nodeConfiguration
    )

    private val outboundSubscription = subscriptionFactory.createPubSubSubscription(
        SubscriptionConfig("outbound-message-handler", LINK_OUT_TOPIC),
        this,
        threadPool,
        nodeConfiguration,
    )
    private val outboundSubscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        outboundSubscription,
        setOf(connectionManager.dominoTile, connectionConfigReader.dominoTile),
        setOf(connectionManager.dominoTile, connectionConfigReader.dominoTile)
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = listOf(outboundSubscriptionTile, trustStoresMap.dominoTile),
        managedChildren = listOf(outboundSubscriptionTile, trustStoresMap.dominoTile)
    )

    override fun onNext(event: Record<String, LinkOutMessage>) {
        dominoTile.withLifecycleLock {
            if (!isRunning) {
                throw IllegalStateException("Can not handle events")
            }

            event.value?.let { peerMessage ->
                try {
                    val trustStore = trustStoresMap.getTrustStore(peerMessage.header.destinationIdentity.groupId)

                    val sni = SniCalculator.calculateSni(
                        peerMessage.header.destinationIdentity.x500Name,
                        peerMessage.header.destinationNetworkType,
                        peerMessage.header.address
                    )
                    val messageId = UUID.randomUUID().toString()
                    val gatewayMessage = GatewayMessage(messageId, peerMessage.payload)
                    val expectedX500Name = if (NetworkType.CORDA_4 == peerMessage.header.destinationNetworkType) {
                        X500Name(peerMessage.header.destinationIdentity.x500Name)
                    } else {
                        null
                    }
                    val destinationInfo = DestinationInfo(
                        URI.create(peerMessage.header.address),
                        sni,
                        expectedX500Name,
                        trustStore,
                    )
                    val responseFuture = sendMessage(destinationInfo, gatewayMessage)
                    scheduleHandleResponse(PendingRequest(gatewayMessage, destinationInfo, responseFuture))
                } catch (e: IllegalArgumentException) {
                    logger.warn("Can't send message to destination ${peerMessage.header.address}. ${e.message}")
                    null
                }
            }
        }
    }

    private fun scheduleHandleResponse(pendingRequest: PendingRequest) {
        threadPool.schedule({
            val (response, error) = try {
                val response = pendingRequest.future.get(0, TimeUnit.MILLISECONDS)
                response to null
            } catch (error: Exception) {
                when {
                    error is ExecutionException && error.cause != null -> null to error.cause
                    else -> null to error
                }
            }
            handleResponse(pendingRequest, response, error, MAX_RETRIES)
        }, connectionConfig().retryDelay.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun replayMessage(destinationInfo: DestinationInfo, gatewayMessage: GatewayMessage, remainingAttempts: Int) {
        val future = sendMessage(destinationInfo, gatewayMessage)
        val pendingRequest = PendingRequest(gatewayMessage, destinationInfo, future)
        future.orTimeout(connectionConfig().responseTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .whenCompleteAsync { response, error ->
                handleResponse(pendingRequest, response, error, remainingAttempts - 1)
            }
    }

    private fun handleResponse(pendingRequest: PendingRequest, response: HttpResponse?, error: Throwable?, remainingAttempts: Int) {
        if (error != null) {
            if (remainingAttempts > 0) {
                logger.warn("Request (${pendingRequest.gatewayMessage.id}) failed, it will be retried later.", error)
                replayMessage(pendingRequest.destinationInfo, pendingRequest.gatewayMessage, remainingAttempts)
            } else {
                logger.warn("Request (${pendingRequest.gatewayMessage.id}) failed.", error)
            }
        } else if (response != null) {
            if (response.statusCode != HttpResponseStatus.OK) {
                if (shouldRetry(response.statusCode) && remainingAttempts > 0) {
                    logger.warn(
                        "Request (${pendingRequest.gatewayMessage.id}) failed with status code ${response.statusCode}, " +
                            "it will be retried later."
                    )
                    replayMessage(pendingRequest.destinationInfo, pendingRequest.gatewayMessage, remainingAttempts)
                } else {
                    logger.warn("Request (${pendingRequest.gatewayMessage.id}) failed with status code ${response.statusCode}.")
                }
            }
        }
    }

    private fun shouldRetry(statusCode: HttpResponseStatus): Boolean {
        return statusCode.code() >= 500
    }

    private fun sendMessage(destinationInfo: DestinationInfo, gatewayMessage: GatewayMessage): CompletableFuture<HttpResponse> {
        logger.debug { "Sending message ${gatewayMessage.payload.javaClass} (${gatewayMessage.id}) to $destinationInfo." }
        return connectionManager.acquire(destinationInfo).write(gatewayMessage.toByteBuffer().array())
    }

    private fun connectionConfig() = connectionConfigReader.connectionConfig

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<LinkOutMessage>
        get() = LinkOutMessage::class.java

    private data class PendingRequest(
        val gatewayMessage: GatewayMessage,
        val destinationInfo: DestinationInfo,
        val future: CompletableFuture<HttpResponse>
    )
}
