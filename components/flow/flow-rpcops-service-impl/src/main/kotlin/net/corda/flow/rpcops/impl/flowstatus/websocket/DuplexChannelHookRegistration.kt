package net.corda.flow.rpcops.impl.flowstatus.websocket

import java.util.UUID
import net.corda.data.identity.HoldingIdentity as AvroHoldingIdentity
import net.corda.flow.rpcops.FlowStatusCacheService
import net.corda.httprpc.ws.DuplexChannel
import net.corda.httprpc.ws.WebSocketProtocolViolationException
import net.corda.v5.base.util.debug
import net.corda.virtualnode.toCorda
import org.slf4j.Logger

fun DuplexChannel.registerFlowStatusFeedHooks(
    flowStatusCacheService: FlowStatusCacheService,
    clientRequestId: String,
    holdingIdentity: AvroHoldingIdentity,
    log: Logger,
) {
    val holdingIdentityShortHash = holdingIdentity.toCorda().shortHash
    val id = UUID.randomUUID()
    this.onConnect = {
        log.debug { "Flow status feed $id connected (clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash)." }
        val listener = WebSocketFlowStatusUpdateListener(id, this, clientRequestId, holdingIdentity)
        try {
            flowStatusCacheService.registerFlowStatusListener(clientRequestId, holdingIdentity, listener)
        } catch (e: Exception) {
            this.error(e)
        }
    }
    this.onClose = { statusCode, reason ->
        log.debug {
            "Closing flow status feed $id with status $statusCode, reason: $reason. " +
                    "(clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash)"
        }
        flowStatusCacheService.unregisterFlowStatusListener(id)
    }
    this.onError = { e ->
        log.info(
            "Flow status feed $id received an error. " +
                    "(clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash)", e
        )
    }
    this.onTextMessage = {
        log.debug { "Flow status feed $id does not support receiving messages. Terminating connection." }
        this.error(WebSocketProtocolViolationException("Inbound messages are not permitted."))
    }
}