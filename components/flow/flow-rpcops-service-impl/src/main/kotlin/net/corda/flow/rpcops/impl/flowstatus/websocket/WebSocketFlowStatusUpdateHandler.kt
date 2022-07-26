package net.corda.flow.rpcops.impl.flowstatus.websocket

import java.time.Instant
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.rpcops.flowstatus.FlowStatusUpdateHandler
import net.corda.flow.rpcops.v1.types.response.FlowStateErrorResponse
import net.corda.flow.rpcops.v1.types.response.FlowStatusResponse
import net.corda.httprpc.ws.DuplexChannel
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.toCorda

/**
 * Flow status update handler that uses websockets to communicate updates to the counterpart connection.
 */
class WebSocketFlowStatusUpdateHandler(
    private val channel: DuplexChannel,
    private val clientRequestId: String,
    private val holdingIdentity: HoldingIdentity,
    private val onCloseCallback: () -> Unit
) : FlowStatusUpdateHandler{

    private companion object {
        val logger = contextLogger()
    }

    // todo conal - not sure if FlowStatus Avro type is correct on this API.
    override fun onFlowStatusUpdate(status: FlowStatus) {
        // todo conal - make first log debug?
        logger.info("Flow ${status.flowStatus.name} for req: $clientRequestId, holdingId: ${holdingIdentity.toCorda().shortHash}")
        logger.info("Flow status: $status")

//        val statusResponse = createFlowStatusResponse(status)
        // todo conal -sending string works but not objects, the client handler can't seem to pick up the objects
        channel.send(status.flowStatus.name)

        if(status.flowStatus == FlowStates.COMPLETED || status.flowStatus == FlowStates.FAILED) {
            logger.info("WebSocket flow status update feed completed with flowStatus: $status")
            completeFeedAndClose()
        }
    }

    private fun completeFeedAndClose() {
        logger.info("Flow completed, closing handler for req: $clientRequestId, holdingId: ${holdingIdentity.toCorda().shortHash}")
        channel.close("Flow complete.")
        onCloseCallback.invoke()
    }

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

    override fun onError(errors: List<String>) {
        logger.info("Received onError callback for req: $clientRequestId, holdingId: ${holdingIdentity.toCorda().shortHash}")
        logger.info("Errors: $errors")
        channel.close()
    }

    override fun close() {
        logger.info("Handler closing for req: $clientRequestId, holdingId: ${holdingIdentity.toCorda().shortHash}")
        channel.close()
        onCloseCallback.invoke()
    }
}