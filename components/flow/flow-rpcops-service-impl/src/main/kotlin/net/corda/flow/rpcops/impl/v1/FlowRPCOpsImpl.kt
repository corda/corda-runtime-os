package net.corda.flow.rpcops.impl.v1

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import net.corda.data.identity.HoldingIdentity
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.flow.rpcops.FlowRPCOpsServiceException
import net.corda.flow.rpcops.FlowStatusCacheService
import net.corda.flow.rpcops.factory.MessageFactory
import net.corda.flow.rpcops.impl.flowstatus.websocket.WebSocketFlowStatusUpdateHandler
import net.corda.flow.rpcops.v1.FlowRpcOps
import net.corda.flow.rpcops.v1.types.request.StartFlowParameters
import net.corda.flow.rpcops.v1.types.request.WebSocketTerminateFlowStatusFeedType
import net.corda.flow.rpcops.v1.types.response.FlowStatusResponse
import net.corda.flow.rpcops.v1.types.response.FlowStatusResponses
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.ResourceAlreadyExistsException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.ws.DuplexChannel
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.Companion.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.Companion.FLOW_STATUS_TOPIC
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(service = [FlowRpcOps::class, PluggableRPCOps::class], immediate = true)
class FlowRPCOpsImpl @Activate constructor(
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = FlowStatusCacheService::class)
    private val flowStatusCacheService: FlowStatusCacheService,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = MessageFactory::class)
    private val messageFactory: MessageFactory
) : FlowRpcOps, PluggableRPCOps<FlowRpcOps>, Lifecycle {

    companion object {
        val log: Logger = contextLogger()
        val PUBLICATION_TIMEOUT_SECONDS = 30L
    }

    override val isRunning: Boolean get() = publisher != null

    override val targetInterface: Class<FlowRpcOps> = FlowRpcOps::class.java
    override val protocolVersion: Int = 1

    var publisher: Publisher? = null

    override fun initialise(config: SmartConfig) {
        publisher?.close()
        publisher = publisherFactory.createPublisher(PublisherConfig("FlowRPCOps"), config)
    }

    @Suppress("SpreadOperator")
    override fun startFlow(
        holdingIdentityShortHash: String,
        startFlow: StartFlowParameters
    ): FlowStatusResponse {
        if (publisher == null) {
            throw FlowRPCOpsServiceException("FlowRPC has not been initialised ")
        }

        val vNode = getVirtualNode(ShortHash.of(holdingIdentityShortHash))
        val clientRequestId = startFlow.clientRequestId
        val flowStatus = flowStatusCacheService.getStatus(clientRequestId, vNode.holdingIdentity)

        if (flowStatus != null) {
            throw ResourceAlreadyExistsException("A flow has already been started with for the requested holdingId and clientRequestId")
        }

        val flowClassName = startFlow.flowClassName
        // TODO Platform properties to be populated correctly, for now a fixed 'account zero' is the only property
        // This is a placeholder which indicates access to everything, see CORE-6076
        val flowContextPlatformProperties = mapOf("net.corda.account" to "account-zero")
        val startEvent =
            messageFactory.createStartFlowEvent(
                clientRequestId,
                vNode,
                flowClassName,
                startFlow.requestData,
                flowContextPlatformProperties
            )
        val status = messageFactory.createStartFlowStatus(clientRequestId, vNode, flowClassName)

        val records = listOf(
            Record(FLOW_MAPPER_EVENT_TOPIC, status.key.toString(), startEvent),
            Record(FLOW_STATUS_TOPIC, status.key, status),
        )

        try {
            val recordFutures = publisher!!.publish(records)
            CompletableFuture.allOf(*recordFutures.toTypedArray())
                .get(PUBLICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw FlowRPCOpsServiceException("Failed to publish the Start Flow event.", e)
        }

        return messageFactory.createFlowStatusResponse(status)
    }

    override fun getFlowStatus(holdingIdentityShortHash: String, clientRequestId: String): FlowStatusResponse {
        val vNode = getVirtualNode(ShortHash.of(holdingIdentityShortHash))

        val flowStatus = flowStatusCacheService.getStatus(clientRequestId, vNode.holdingIdentity)
            ?: throw ResourceNotFoundException(
                "Failed to find the flow status for holding identity='${holdingIdentityShortHash} " +
                        "and Client Request ID='${clientRequestId}"
            )

        return messageFactory.createFlowStatusResponse(flowStatus)
    }

    override fun getMultipleFlowStatus(holdingIdentityShortHash: String): FlowStatusResponses {
        val vNode = getVirtualNode(ShortHash.of(holdingIdentityShortHash))
        val flowStatuses = flowStatusCacheService.getStatusesPerIdentity(vNode.holdingIdentity)
        return FlowStatusResponses(flowStatusResponses = flowStatuses.map { messageFactory.createFlowStatusResponse(it) })
    }

    override fun flowStatusUpdatesFeed(
        channel: DuplexChannel,
        holdingIdentityShortHash: String,
        clientRequestId: String
    ) {
        val holdingIdentity = try {
            getVirtualNode(holdingIdentityShortHash).holdingIdentity
        } catch (e: FlowRPCOpsServiceException) {
            log.info("Could not find virtual node for req: $clientRequestId, holdingId: $holdingIdentityShortHash. Creating later.")
            channel.close()
            throw e
        }
        channel.incomingMessageType = WebSocketTerminateFlowStatusFeedType::class.java
        channel.outgoingMessageType = FlowStatusResponse::class.java
        channel.onConnect = {
            log.info("onConnect called for websocket req: $clientRequestId, holdingId: $holdingIdentityShortHash")
            val handler = WebSocketFlowStatusUpdateHandler(channel, clientRequestId, holdingIdentity) {
                unregisterFlowStatusFeed(clientRequestId, holdingIdentity)
            }
            try {
                flowStatusCacheService.registerFlowStatusFeed(clientRequestId, holdingIdentity, handler)
            } catch (e: Exception) {
                channel.error(e)
            }
        }
        channel.onClose = { statusCode, reason ->
            // todo conal - close seems to be called twice (example the status feed has completed)
            log.info("onClose called for websocket req: $clientRequestId, holdingId: $holdingIdentityShortHash")
            log.info("StatusCode: $statusCode, reason: $reason")
            unregisterFlowStatusFeed(clientRequestId, holdingIdentity)
        }
        channel.onError = { e ->
            log.info("onError called for websocket req: $clientRequestId, holdingId: $holdingIdentityShortHash", e)
            unregisterFlowStatusFeed(clientRequestId, holdingIdentity)
        }
        channel.onTextMessage = { message ->
            log.info("onTextMessage called for websocket req: $clientRequestId, holdingId: $holdingIdentityShortHash")
            when (message) {
                is WebSocketTerminateFlowStatusFeedType -> {
                    log.info("Terminating feed for req: $clientRequestId, holdingId: $holdingIdentityShortHash")
                    channel.close()
                    unregisterFlowStatusFeed(clientRequestId, holdingIdentity)
                }
                else -> {
                    log.info("Unknown message for req: $clientRequestId, holdingId: $holdingIdentityShortHash. Terminating connection.")
                    channel.close()
                    unregisterFlowStatusFeed(clientRequestId, holdingIdentity)
                }
            }
        }
    }

    private fun unregisterFlowStatusFeed(clientRequestId: String, holdingIdentity: HoldingIdentity) {
        log.info("Unregistering flow status feed for request: $clientRequestId and identity: ${holdingIdentity.shortHash()}.")
        flowStatusCacheService.unregisterFlowStatusFeed(clientRequestId, holdingIdentity)
    }

    private fun HoldingIdentity.shortHash() = this.toCorda().shortHash

    override fun start() = Unit

    override fun stop() {
        publisher?.close()
    }

    private fun getVirtualNode(shortId: ShortHash): VirtualNodeInfo {
        return virtualNodeInfoReadService.getByHoldingIdentityShortHash(shortId)?.toAvro()
            ?: throw FlowRPCOpsServiceException("Failed to find a Virtual Node for ID='${shortId}'")
    }
}
