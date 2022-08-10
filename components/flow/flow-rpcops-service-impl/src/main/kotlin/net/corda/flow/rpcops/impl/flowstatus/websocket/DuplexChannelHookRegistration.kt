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
import net.corda.v5.base.util.debug
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import java.util.concurrent.TimeUnit
import net.corda.httprpc.ws.WebSocketValidationException

fun DuplexChannel.registerFlowStatusFeedHooks(
    flowStatusCacheService: FlowStatusCacheService,
    clientRequestId: String,
    holdingIdentity: AvroHoldingIdentity,
    log: Logger,
) {
    val holdingIdentityShortHash = holdingIdentity.toCorda().shortHash
    val id = UUID.randomUUID()
    val listener: FlowStatusUpdateListener = WebSocketFlowStatusUpdateListener(
        id,
        clientRequestId,
        holdingIdentity,
        onCloseCallback(),
        onStatusUpdate(log, holdingIdentity, clientRequestId)
    )
    onClose = { statusCode, reason ->
        log.info(
            "Close hook called for id ${listener.id} with status $statusCode, reason: $reason. Closing listener. " +
                    "(clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash)"
        )
        flowStatusCacheService.unregisterFlowStatusListener(clientRequestId, holdingIdentity, listener)
    }
    onError = { e ->
        log.warn(
            "Error hook called for id ${listener.id}. Closing listener. " +
                    "(clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash)",
            e
        )
    }
    onTextMessage = {
        log.debug { "Flow status feed ${listener.id} does not support receiving messages. Terminating connection." }
        error(WebSocketProtocolViolationException("Inbound messages are not permitted."))
    }
    onConnect = {
        log.debug { "Flow status feed $id connected (clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash)." }
        try {
            flowStatusCacheService.registerFlowStatusListener(clientRequestId, holdingIdentity, listener)
        } catch (e: WebSocketValidationException) {
            log.warn("Validation error while registering flow status listener - ${e.message}")
            error(e)
        } catch (e: Exception) {
            log.error("Unexpected error at registerFlowStatusListener")
            error(e)
        }
    }
}

private fun DuplexChannel.onStatusUpdate(log: Logger, holdingIdentity: AvroHoldingIdentity, clientRequestId: String) =
    { avroStatus: FlowStatus ->
        try {
            val future = send(avroStatus.createFlowStatusResponse())
            if (avroStatus.flowStatus.isFlowFinished()) {
                try {
                    future.get(10, TimeUnit.SECONDS)
                } catch (ex: Exception) {
                    log.error("Could not send terminal state to the remote side", ex)
                }

                log.info(
                    "Flow ${avroStatus.flowStatus}. Closing WebSocket connection(s) for " +
                            "clientRequestId: $clientRequestId, holdingId: ${holdingIdentity.toCorda().shortHash}"
                )
                close("Flow ${avroStatus.flowStatus.name} since it is a terminal state")
            }
        } catch (ex: Exception) {
            log.error("Unexpected error when processing FlowStatus update")
        }
    }

private fun DuplexChannel.onCloseCallback() = { message: String -> close(message) }

private fun FlowStates.isFlowFinished() = this == FlowStates.COMPLETED || this == FlowStates.FAILED

private fun FlowStatus.createFlowStatusResponse(): FlowStatusResponse {
    return FlowStatusResponse(
        key.identity.toCorda().shortHash.value,
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
