package net.corda.flow.rpcops.impl.flowstatus.websocket

import java.util.UUID
import net.corda.data.identity.HoldingIdentity as AvroHoldingIdentity
import net.corda.flow.rpcops.FlowStatusCacheService
import net.corda.httprpc.ws.DuplexChannel
import net.corda.httprpc.ws.WebSocketProtocolViolationException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
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
        val id = UUID.randomUUID()
        channel.onConnect = {
            log.debug { "Flow status feed $id connected (clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash)." }
            val listener = WebSocketFlowStatusUpdateListener(id, channel, clientRequestId, holdingIdentity)
            try {
                flowStatusCacheService.registerFlowStatusFeed(clientRequestId, holdingIdentity, listener)
            } catch (e: Exception) {
                channel.error(e)
            }
        }
        channel.onClose = { statusCode, reason ->
            log.debug { "Closing flow status feed $id with status $statusCode, reason: $reason. " +
                    "(clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash)" }
            unregisterFlowStatusFeed(id)
        }
        channel.onError = { e ->
            log.info("Flow status feed $id received an error. " +
                    "(clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash)", e)
        }
        channel.onTextMessage = {
            log.debug { "Flow status feed $id does not support receiving messages. Terminating connection." }
            channel.error(WebSocketProtocolViolationException("Inbound messages are not permitted."))
        }
    }

    private fun unregisterFlowStatusFeed(handlerId: UUID) {
        flowStatusCacheService.unregisterFlowStatusFeed(handlerId)
    }
}