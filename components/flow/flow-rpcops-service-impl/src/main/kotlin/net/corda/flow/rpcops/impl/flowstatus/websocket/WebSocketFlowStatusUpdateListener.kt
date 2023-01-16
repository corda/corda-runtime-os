package net.corda.flow.rpcops.impl.flowstatus.websocket

import java.time.Instant
import java.util.concurrent.TimeUnit
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.rpcops.flowstatus.FlowStatusUpdateListener
import net.corda.flow.rpcops.v1.types.response.FlowStateErrorResponse
import net.corda.flow.rpcops.v1.types.response.FlowStatusResponse
import net.corda.httprpc.ws.DuplexChannel
import net.corda.httprpc.ws.WebSocketProtocolViolationException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.toCorda

/**
 * Flow status update handler that uses websockets to communicate updates to the counterpart connection.
 */
class WebSocketFlowStatusUpdateListener(
    private val clientRequestId: String,
    holdingIdentity: HoldingIdentity,
    private val duplexChannel: DuplexChannel
) : FlowStatusUpdateListener {

    private companion object {
        val logger = contextLogger()
    }

    override val id: String
        get() = duplexChannel.id

    private val holdingIdentityShortHash = holdingIdentity.toCorda().shortHash

    init {
        duplexChannel.onError = { e ->
            logger.warn(
                "Error hook called for duplex channel $id. " +
                        "(clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash)",
                e
            )
        }
        duplexChannel.onTextMessage = {
            logger.debug { "Flow status feed $id does not support receiving messages. Terminating connection." }
            duplexChannel.error(WebSocketProtocolViolationException("Inbound messages are not permitted."))
        }
        duplexChannel.onConnect = {
            logger.info("Flow status feed $id connected (clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash).")
        }
    }

    // Potentially this can be invoked from multiple threads
    @Synchronized
    override fun updateReceived(status: FlowStatus) {
        logger.info(
            "Listener $id received flow status update: ${status.flowStatus.name} (clientRequestId: $clientRequestId, " +
                    "holdingId: ${holdingIdentityShortHash})."
        )

        onStatusUpdate(status)
    }

    override fun close(message: String) {
        duplexChannel.close(message)
    }

    override fun close() {
        close("Listener closed.")
    }

    private fun onStatusUpdate(avroStatus: FlowStatus) {
        try {
            logger.info("Sending flow status update (${avroStatus.flowStatus.name}) to session ${this.id}.")
            val future = duplexChannel.send(avroStatus.createFlowStatusResponse())

            if (avroStatus.flowStatus.isFlowFinished()) {
                try {
                    future.get(10, TimeUnit.SECONDS)
                } catch (ex: Exception) {
                    logger.error("Could not send terminal state to the remote side", ex)
                }

                logger.info(
                    "Flow ${avroStatus.flowStatus}. Closing WebSocket connection(s) for " +
                            "clientRequestId: $clientRequestId, holdingId: $holdingIdentityShortHash"
                )
                close("Flow ${avroStatus.flowStatus.name} since it is a terminal state")
            }
        } catch (ex: Exception) {
            logger.error("Unexpected error when processing FlowStatus update")
        }
    }

    private fun FlowStates.isFlowFinished() = this == FlowStates.COMPLETED || this == FlowStates.FAILED || this == FlowStates.KILLED

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
}