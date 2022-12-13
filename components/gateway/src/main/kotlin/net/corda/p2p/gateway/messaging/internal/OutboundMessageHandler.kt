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
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.debug
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * This is an implementation of an [PubSubProcessor] used to consume messages from a P2P message subscription. The received
 * events are processed and fed into the HTTP pipeline.
 */
@Suppress("LongParameterList")
internal class OutboundMessageHandler(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    configurationReaderService: ConfigurationReadService,
    subscriptionFactory: SubscriptionFactory,
    messagingConfiguration: SmartConfig,
    private val avroSchemaRegistry: AvroSchemaRegistry,
    retryThreadPoolFactory: () -> ScheduledExecutorService = { Executors.newSingleThreadScheduledExecutor() },
) : PubSubProcessor<String, LinkOutMessage>, LifecycleWithDominoTile {

    companion object {
        private val logger = LoggerFactory.getLogger(OutboundMessageHandler::class.java)
        private const val MAX_RETRIES = 1
    }

    private val connectionConfigReader = ConnectionConfigReader(lifecycleCoordinatorFactory, configurationReaderService)

    private val connectionManager = ReconfigurableConnectionManager(
        lifecycleCoordinatorFactory,
        configurationReaderService
    )

    private val trustStoresMap = TrustStoresMap(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        messagingConfiguration
    )

    private val subscriptionConfig = SubscriptionConfig("outbound-message-handler", LINK_OUT_TOPIC)
    private val outboundSubscription = {
        subscriptionFactory.createPubSubSubscription(
            subscriptionConfig,
            this,
            messagingConfiguration,
        )
    }
    private val outboundSubscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        outboundSubscription,
        subscriptionConfig,
        setOf(connectionManager.dominoTile.coordinatorName, connectionConfigReader.dominoTile.coordinatorName),
        setOf(connectionManager.dominoTile.toNamedLifecycle(), connectionConfigReader.dominoTile.toNamedLifecycle())
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        onClose = { retryThreadPool.shutdown() },
        dependentChildren = listOf(outboundSubscriptionTile.coordinatorName, trustStoresMap.dominoTile.coordinatorName),
        managedChildren = listOf(outboundSubscriptionTile.toNamedLifecycle(), trustStoresMap.dominoTile.toNamedLifecycle()),
    )

    private val retryThreadPool = retryThreadPoolFactory()

    override fun onNext(event: Record<String, LinkOutMessage>): CompletableFuture<Unit> {
        return dominoTile.withLifecycleLock {
            if (!isRunning) {
                throw IllegalStateException("Can not handle events")
            }

            val peerMessage = event.value
            return@withLifecycleLock if (peerMessage != null) {
                try {
                    val trustStore = trustStoresMap.getTrustStore(
                        MemberX500Name.parse(peerMessage.header.sourceIdentity.x500Name),
                        peerMessage.header.destinationIdentity.groupId
                    )

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
                        .orTimeout(connectionConfig().responseTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    responseFuture.whenCompleteAsync({ response, error ->
                        handleResponse(PendingRequest(gatewayMessage, destinationInfo, responseFuture), response, error, MAX_RETRIES)
                    }, retryThreadPool).thenApply { Unit }
                } catch (e: IllegalArgumentException) {
                    logger.warn("Can't send message to destination ${peerMessage.header.address}. ${e.message}")
                    CompletableFuture.completedFuture(Unit)
                }
            } else {
                logger.warn("Received a null message from topic $LINK_OUT_TOPIC. The message was discarded.")
                CompletableFuture.completedFuture(Unit)
            }
        }
    }

    private fun scheduleMessageReplay(destinationInfo: DestinationInfo, gatewayMessage: GatewayMessage, remainingAttempts: Int) {
        retryThreadPool.schedule({
            val future = sendMessage(destinationInfo, gatewayMessage)
            val pendingRequest = PendingRequest(gatewayMessage, destinationInfo, future)
            future.orTimeout(connectionConfig().responseTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenCompleteAsync { response, error ->
                    handleResponse(pendingRequest, response, error, remainingAttempts - 1)
                }
        }, connectionConfig().retryDelay.toMillis(), TimeUnit.MILLISECONDS)
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
                    logger.warn(
                        "Request (${pendingRequest.gatewayMessage.id}) failed with status code ${response.statusCode}, " +
                            "it will be retried later."
                    )
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

    private fun sendMessage(destinationInfo: DestinationInfo, gatewayMessage: GatewayMessage): CompletableFuture<HttpResponse> {
        logger.debug { "Sending message ${gatewayMessage.payload.javaClass} (${gatewayMessage.id}) to $destinationInfo." }
        return connectionManager.acquire(destinationInfo).write(avroSchemaRegistry.serialize(gatewayMessage).array())
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
