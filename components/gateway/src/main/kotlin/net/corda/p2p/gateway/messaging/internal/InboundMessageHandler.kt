package net.corda.p2p.gateway.messaging.internal

import io.micrometer.core.instrument.Timer
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.app.InboundUnauthenticatedMessage
import net.corda.data.p2p.crypto.AuthenticatedDataMessage
import net.corda.data.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.data.p2p.crypto.ResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage
import net.corda.data.p2p.gateway.GatewayMessage
import net.corda.data.p2p.gateway.GatewayResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.metrics.CordaMetrics
import net.corda.metrics.CordaMetrics.NOT_APPLICABLE_TAG_VALUE
import net.corda.p2p.gateway.messaging.http.HttpRequest
import net.corda.p2p.gateway.messaging.http.HttpWriter
import net.corda.p2p.gateway.messaging.http.ReconfigurableHttpServer
import net.corda.p2p.gateway.messaging.mtls.DynamicCertificateSubjectStore
import net.corda.p2p.gateway.messaging.session.SessionPartitionMapperImpl
import net.corda.schema.Schemas.P2P.LINK_IN_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.utilities.Context
import org.apache.avro.SystemLimitException
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.time.Duration
import java.util.UUID
import kotlin.random.Random

/**
 * This class implements a simple message processor for p2p messages received from other Gateways.
 */
@Suppress("LongParameterList")
internal class InboundMessageHandler(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    configurationReaderService: ConfigurationReadService,
    publisherFactory: PublisherFactory,
    subscriptionFactory: SubscriptionFactory,
    messagingConfiguration: SmartConfig,
    private val commonComponents: CommonComponents,
    private val avroSchemaRegistry: AvroSchemaRegistry,
    platformInfoProvider: PlatformInfoProvider,
    bootConfig: SmartConfig,
) : RequestListener, LifecycleWithDominoTile {

    init {
        // Setting max limits for variable-length fields to prevent malicious clients from trying to trigger large memory allocations.
        System.setProperty(SystemLimitException.MAX_BYTES_LENGTH_PROPERTY, AVRO_LIMIT.toString())
        System.setProperty(SystemLimitException.MAX_STRING_LENGTH_PROPERTY, AVRO_LIMIT.toString())
        
        // Need to call package private method for changes to have effect
        // The fact that [SystemLimitException] is using static initializer is not nice, especially given that is class 
        // loaded when [AvroSchemaRegistry] is created.
        val declaredMethod = SystemLimitException::class.java.getDeclaredMethod("resetLimits")
        declaredMethod.setAccessible(true)
        declaredMethod.invoke(null)
    }

    companion object {
        const val AVRO_LIMIT = 5_000_000
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var p2pInPublisher = PublisherWithDominoLogic(
        publisherFactory,
        lifecycleCoordinatorFactory,
        PublisherConfig("inbound-message-handler", false),
        messagingConfiguration
    )
    private val sessionPartitionMapper = SessionPartitionMapperImpl(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        messagingConfiguration
    )

    private val dynamicCertificateSubjectStore = DynamicCertificateSubjectStore(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        messagingConfiguration
    )
    private val linkManagerClient =
        LinkManagerRpcClient(
            publisherFactory,
            platformInfoProvider,
            bootConfig,
        )

    private val server = ReconfigurableHttpServer(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        this,
        commonComponents,
        dynamicCertificateSubjectStore,
    )
    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = listOf(
            sessionPartitionMapper.dominoTile.coordinatorName,
            p2pInPublisher.dominoTile.coordinatorName,
            server.dominoTile.coordinatorName,
            dynamicCertificateSubjectStore.dominoTile.coordinatorName,
        ),
        managedChildren = listOf(
            sessionPartitionMapper.dominoTile.toNamedLifecycle(),
            p2pInPublisher.dominoTile.toNamedLifecycle(),
            server.dominoTile.toNamedLifecycle(),
            dynamicCertificateSubjectStore.dominoTile.toNamedLifecycle(),
        )
    )

    /**
     * Handler for direct P2P messages. The payload is deserialized and then published to the ingress topic.
     * A session init request has additional handling as the Gateway needs to generate a secret and share it
     */
    override fun onRequest(httpWriter: HttpWriter, request: HttpRequest) {
        dominoTile.withLifecycleLock {
            val startTime = System.nanoTime()
            val statusCode = handleRequest(httpWriter, request)
            val duration = Duration.ofNanos(System.nanoTime() - startTime)
            getRequestTimer(request.source, statusCode).record(duration)
        }
    }

    private fun handleRequest(httpWriter: HttpWriter, request: HttpRequest): HttpResponseStatus {
        if (!isRunning) {
            logger.error("Received message from ${request.source}, while handler is stopped. Discarding it and returning error code.")
            httpWriter.write(HttpResponseStatus.SERVICE_UNAVAILABLE, request.source)
            return HttpResponseStatus.SERVICE_UNAVAILABLE
        }

        val (gatewayMessage, p2pMessage) = try {
            val gatewayMessage = avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(request.payload))
            logger.info("Received  message: ${gatewayMessage.id}")
            gatewayMessage to LinkInMessage(gatewayMessage.payload)
        } catch (e: Throwable) {
            logger.warn("Received invalid message, which could not be deserialized", e)
            httpWriter.write(HttpResponseStatus.BAD_REQUEST, request.source)
            return HttpResponseStatus.BAD_REQUEST
        }

        logger.debug("Received and processing message {} of type {} from {}",
            gatewayMessage.id, p2pMessage.payload::class.java, request.source)
        return if (commonComponents.features.enableP2PGatewayToLinkManagerOverHttp) {
            return forwardMessage(
                httpWriter,
                request.source,
                gatewayMessage.id,
                p2pMessage,
            )
        } else {
            val response = GatewayResponse(
                gatewayMessage.id,
                null,
            )
            when (p2pMessage.payload) {
                is InboundUnauthenticatedMessage -> {
                    val key = "${gatewayMessage.id}:${generateKey()}"
                    Context.myLog("For ${gatewayMessage.id} key will be $key")
                    p2pInPublisher.publish(listOf(Record(LINK_IN_TOPIC, key, p2pMessage)))
                    httpWriter.write(HttpResponseStatus.OK, request.source, avroSchemaRegistry.serialize(response).array())
                    HttpResponseStatus.OK
                }
                else -> {
                    val statusCode = processSessionMessage(p2pMessage, gatewayMessage.id)
                    httpWriter.write(statusCode, request.source, avroSchemaRegistry.serialize(response).array())
                    statusCode
                }
            }
        }
    }

    private fun forwardMessage(
        httpWriter: HttpWriter,
        requestSource: SocketAddress,
        messageId: String,
        p2pMessage: LinkInMessage,
    ): HttpResponseStatus {
        val payload = try {
            linkManagerClient.send(p2pMessage)
        } catch (e: Exception) {
            logger.warn("could not forward message with ID: $messageId to the link manager.", e)
            val response = GatewayResponse(
                messageId,
                null,
            )
            httpWriter.write(
                INTERNAL_SERVER_ERROR,
                requestSource,
                avroSchemaRegistry.serialize(response).array(),
            )
            return INTERNAL_SERVER_ERROR
        }
        val response = GatewayResponse(
            messageId,
            payload?.payload,
        )
        httpWriter.write(
            HttpResponseStatus.OK,
            requestSource,
            avroSchemaRegistry.serialize(response).array(),
        )
        return HttpResponseStatus.OK
    }

    private fun processSessionMessage(p2pMessage: LinkInMessage, id: String): HttpResponseStatus {
        val sessionId = getSessionId(p2pMessage) ?: return INTERNAL_SERVER_ERROR
        if (p2pMessage.payload is InitiatorHelloMessage) {
            /* we are using the session identifier as key to ensure replayed initiator hello messages will end up on the same partition, and
             * thus processed by the same link manager instance under normal conditions. */
            p2pInPublisher.publish(listOf(Record(LINK_IN_TOPIC, sessionId, p2pMessage)))
            return HttpResponseStatus.OK
        }
        val key = "$id-${Random.nextInt(5000)}"
        Context.myLog("processSessionMessage for session ID: $sessionId message $id, key $key")
        val record = Record(LINK_IN_TOPIC, key, p2pMessage)
        if (commonComponents.features.useStatefulSessionManager) {
            p2pInPublisher.publish(listOf(record))
            return HttpResponseStatus.OK
        } else {
            val partitions = sessionPartitionMapper.getPartitions(sessionId)
            return if (partitions == null) {
                logger.warn("No mapping for session ($sessionId), discarding the message and returning an error.")
                HttpResponseStatus.GONE
            } else if (partitions.isEmpty()) {
                logger.warn("No partitions exist for session ($sessionId), discarding the message and returning an error.")
                HttpResponseStatus.GONE
            } else {
                // this is simplistic (stateless) load balancing amongst the partitions owned by the LM that "hosts" the session.
                val selectedPartition = partitions.random()
                p2pInPublisher.publishToPartition(listOf(selectedPartition to record))
                HttpResponseStatus.OK
            }
        }
    }

    private fun getSessionId(message: LinkInMessage): String? {
        return when (message.payload) {
            is AuthenticatedDataMessage -> (message.payload as AuthenticatedDataMessage).header.sessionId
            is AuthenticatedEncryptedDataMessage -> (message.payload as AuthenticatedEncryptedDataMessage).header.sessionId
            is InitiatorHelloMessage -> (message.payload as InitiatorHelloMessage).header.sessionId
            is InitiatorHandshakeMessage -> (message.payload as InitiatorHandshakeMessage).header.sessionId
            is ResponderHelloMessage -> (message.payload as ResponderHelloMessage).header.sessionId
            is ResponderHandshakeMessage -> (message.payload as ResponderHandshakeMessage).header.sessionId
            else -> {
                logger.warn("Invalid payload of LinkInMessage: ${message.payload::class.java}")
                return null
            }
        }
    }

    private fun generateKey(): String {
        return UUID.randomUUID().toString()
    }

    private fun getRequestTimer(sourceAddress: SocketAddress, statusCode: HttpResponseStatus): Timer {
        val metricsBuilder = CordaMetrics.Metric.InboundGatewayRequestLatency.builder()
        metricsBuilder.withTag(CordaMetrics.Tag.HttpResponseType, statusCode.code().toString())
        // The address is always expected to be an InetSocketAddress, but populating the tag in any other case because prometheus
        // requires the same set of tags to be populated for a specific metric name.
        val sourceEndpoint = if (sourceAddress is InetSocketAddress) {
            sourceAddress.hostString
        } else {
            NOT_APPLICABLE_TAG_VALUE
        }
        metricsBuilder.withTag(CordaMetrics.Tag.SourceEndpoint, sourceEndpoint)
        return metricsBuilder.build()
    }
}
