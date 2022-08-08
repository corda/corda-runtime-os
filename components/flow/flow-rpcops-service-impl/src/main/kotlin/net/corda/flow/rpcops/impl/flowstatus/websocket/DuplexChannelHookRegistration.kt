package net.corda.flow.rpcops.impl.flowstatus.websocket

import java.time.Instant
import java.util.UUID
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity as AvroHoldingIdentity
import net.corda.flow.rpcops.FlowStatusCacheService
import net.corda.flow.rpcops.flowstatus.FlowStatusUpdateListener
import net.corda.flow.rpcops.v1.types.response.FlowStateErrorResponse
import net.corda.flow.rpcops.v1.types.response.FlowStatusResponse
import net.corda.httprpc.ws.DuplexChannel
import net.corda.httprpc.ws.WebSocketProtocolViolationException
import net.corda.virtualnode.toCorda
import org.slf4j.Logger

fun DuplexChannel.registerFlowStatusFeedHooks(
    flowStatusCacheService: FlowStatusCacheService,
    clientRequestId: String,
    holdingIdentity: AvroHoldingIdentity,
    log: Logger,
) {
    val holdingIdentityShortHash = holdingIdentity.toCorda().shortHash
    var listener: FlowStatusUpdateListener? = null
    onConnect = {
        val id = UUID.randomUUID()
        log.info("Flow status feed $id connected (clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash).")
        listener = WebSocketFlowStatusUpdateListener(
            id,
            clientRequestId,
            holdingIdentity,
            onCloseCallback(),
            onStatusUpdate(log, holdingIdentity, clientRequestId)
        ).also {
            try {
                flowStatusCacheService.registerFlowStatusListener(clientRequestId, holdingIdentity, it)
            } catch (e: Exception) {
                log.error("Unexpected error at registerFlowStatusListener")
                error(e)
            }
        }
    }
    onClose = { statusCode, reason ->
        log.info(
            "Close hook called for id ${listener?.id} with status $statusCode, reason: $reason. " +
                    "(clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash)"
        )
        listener?.let {
            flowStatusCacheService.unregisterFlowStatusListener(clientRequestId, holdingIdentity, it)
        }
    }
    onError = { e ->
        log.info(
            "Flow status feed ${listener?.id} received an error. " +
                    "(clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash)", e
        )
    }
    onTextMessage = {
        log.info("Flow status feed ${listener?.id} does not support receiving messages. Terminating connection.")
        error(WebSocketProtocolViolationException("Inbound messages are not permitted."))
    }
}

private fun DuplexChannel.onStatusUpdate(log: Logger, holdingIdentity: AvroHoldingIdentity, clientRequestId: String) =
    { avroStatus: FlowStatus ->
        send(avroStatus.createFlowStatusResponse())
        if (avroStatus.flowStatus.isFlowFinished()) {
            log.info(
                "Flow ${avroStatus.flowStatus}. Closing WebSocket connection(s) for " +
                        "holdingId: ${holdingIdentity.toCorda().shortHash}, clientRequestId: $clientRequestId"
            )
            close("Flow ${avroStatus.flowStatus.name}")
        }
    }

private fun DuplexChannel.onCloseCallback() = { message: String -> close(message) }

private fun FlowStates.isFlowFinished() = this == FlowStates.COMPLETED || this == FlowStates.FAILED

private fun FlowStatus.createFlowStatusResponse(): FlowStatusResponse {
    return FlowStatusResponse(
        key.identity.toCorda().shortHash,
        key.id,
        flowId,
        flowStatus.toString(),
        result,
        if (error != null) FlowStateErrorResponse(
            error.errorType,
            error.errorMessage
        ) else null,
        Instant.now()
    )
}
