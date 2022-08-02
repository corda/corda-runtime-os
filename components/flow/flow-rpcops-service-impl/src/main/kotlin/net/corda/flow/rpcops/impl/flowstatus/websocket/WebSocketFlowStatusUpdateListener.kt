package net.corda.flow.rpcops.impl.flowstatus.websocket

import java.time.Instant
import java.util.UUID
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.rpcops.flowstatus.FlowStatusUpdateListener
import net.corda.flow.rpcops.v1.types.response.FlowStateErrorResponse
import net.corda.flow.rpcops.v1.types.response.FlowStatusResponse
import net.corda.httprpc.ws.DuplexChannel
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.toCorda

/**
 * Flow status update handler that uses websockets to communicate updates to the counterpart connection.
 */
class WebSocketFlowStatusUpdateListener(
    override val id: UUID,
    private val channel: DuplexChannel,
    private val clientRequestId: String,
    private val holdingIdentity: HoldingIdentity
) : FlowStatusUpdateListener {

    private companion object {
        val logger = contextLogger()
    }

    override fun updateReceived(status: FlowStatus) {
        logger.debug {
            "Flow status update: ${status.flowStatus.name} for listener $id, " +
                    "holdingId: ${holdingIdentity.toCorda().shortHash}, clientRequestId: $clientRequestId."
        }

        val statusResponse = createFlowStatusResponse(status)
        channel.send(statusResponse)

        if (status.flowStatus.isFlowFinished()) {
            logger.debug {
                "Flow ${status.flowStatus}. Closing WebSocket connection(s) for " +
                        "holdingId: ${holdingIdentity.toCorda().shortHash}, clientRequestId: $clientRequestId"
            }
            channel.close("Flow ${status.flowStatus.name}.")
        }
    }

    private fun FlowStates.isFlowFinished() = this == FlowStates.COMPLETED || this == FlowStates.FAILED

    private fun createFlowStatusResponse(flowStatus: FlowStatus): FlowStatusResponse {

        return FlowStatusResponse(
            flowStatus.key.identity.toCorda().shortHash,
            flowStatus.key.id,
            flowStatus.flowId,
            flowStatus.flowStatus.toString(),
            flowStatus.result,
            if (flowStatus.error != null) FlowStateErrorResponse(
                flowStatus.error.errorType,
                flowStatus.error.errorMessage
            ) else null,
            Instant.now()
        )
    }

    override fun close() {
        channel.close()
    }
}