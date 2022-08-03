package net.corda.flow.rpcops.impl.flowstatus.websocket

import java.time.Instant
import java.util.UUID
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity as AvroHoldingIdentity
import net.corda.flow.rpcops.FlowStatusCacheService
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
    val id = UUID.randomUUID()
    onConnect = {
        log.info("Flow status feed $id connected (clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash).")
        val listener = WebSocketFlowStatusUpdateListener(
            id,
            clientRequestId,
            holdingIdentity,
            onCloseCallback(),
            onStatusUpdate(log, holdingIdentity, clientRequestId)
        )
        try {
            flowStatusCacheService.registerFlowStatusListener(clientRequestId, holdingIdentity, listener)
        } catch (e: Exception) {
            error(e)
        }
    }
    onClose = { statusCode, reason ->
        log.info(
            "Closing flow status feed $id with status $statusCode, reason: $reason. " +
                    "(clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash)"
        )
        flowStatusCacheService.unregisterFlowStatusListener(id)
    }
    onError = { e ->
        log.info(
            "Flow status feed $id received an error. " +
                    "(clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash)", e
        )
    }
    onTextMessage = {
        log.info("Flow status feed $id does not support receiving messages. Terminating connection.")
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
