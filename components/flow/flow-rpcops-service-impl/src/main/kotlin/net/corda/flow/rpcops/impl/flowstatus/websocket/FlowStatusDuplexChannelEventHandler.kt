package net.corda.flow.rpcops.impl.flowstatus.websocket

import java.util.UUID
import net.corda.data.identity.HoldingIdentity as AvroHoldingIdentity
import net.corda.flow.rpcops.FlowStatusCacheService
import net.corda.httprpc.ws.DuplexChannel
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.toCorda

class FlowStatusDuplexChannelEventHandler(
    private val flowStatusCacheService: FlowStatusCacheService,
    private val channel: DuplexChannel,
    private val clientRequestId: String,
    private val holdingIdentity: AvroHoldingIdentity
) {

    private companion object {
        val log = contextLogger()
    }

    init {
        val holdingIdentityShortHash = holdingIdentity.toCorda().shortHash
        val handlerId = UUID.randomUUID()
        channel.onConnect = {
            log.info("Websocket onConnect received for websocket req: $clientRequestId, holdingId: $holdingIdentityShortHash")
            val handler = WebSocketFlowStatusUpdateListener(handlerId, channel, clientRequestId, holdingIdentity)
            try {
                flowStatusCacheService.registerFlowStatusUpdatesHandler(clientRequestId, holdingIdentity, handler)
            } catch (e: Exception) {
                channel.error(e)
            }
        }
        channel.onClose = { statusCode, reason ->
            log.info("onClose called for websocket req: $clientRequestId, holdingId: $holdingIdentityShortHash with status $statusCode " +
                    "and reason: $reason")
            unregisterFlowStatusFeed(handlerId)
        }
        channel.onError = { e ->
            log.info("onError called for websocket req: $clientRequestId, holdingId: $holdingIdentityShortHash", e)
        }
        channel.onTextMessage = { message ->
            log.info("onTextMessage called for message: $message websocket req: $clientRequestId, holdingId: $holdingIdentityShortHash")
            // just closing for now assuming the message is a termination request
            channel.close()
//            when (message) {
//                is WebSocketTerminateFlowStatusFeedType -> {
//                    log.info("Terminating feed for req: $clientRequestId, holdingId: $holdingIdentityShortHash")
//                    channel.close()
//                    unregisterFlowStatusFeed(clientRequestId, holdingIdentity)
//                }
//                else -> {
//                    log.info("Unknown message for req: $clientRequestId, holdingId: $holdingIdentityShortHash. Terminating connection.")
//                    channel.close()
//                    unregisterFlowStatusFeed(clientRequestId, holdingIdentity)
//                }
//            }
        }
    }

    private fun unregisterFlowStatusFeed(handlerId: UUID) {
        flowStatusCacheService.unregisterFlowStatusFeed(handlerId)
    }
}