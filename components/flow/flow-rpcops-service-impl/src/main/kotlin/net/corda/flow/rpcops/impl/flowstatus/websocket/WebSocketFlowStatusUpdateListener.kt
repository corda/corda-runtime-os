package net.corda.flow.rpcops.impl.flowstatus.websocket

import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.rpcops.flowstatus.FlowStatusUpdateListener
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.toCorda

/**
 * Flow status update handler that uses websockets to communicate updates to the counterpart connection.
 */
class WebSocketFlowStatusUpdateListener(
    override val id: String,
    private val clientRequestId: String,
    private val holdingIdentity: HoldingIdentity,
    private val onCloseCallback: (String) -> Unit,
    private val onUpdate: (FlowStatus) -> Unit
) : FlowStatusUpdateListener {

    private companion object {
        val logger = contextLogger()
    }

    // Potentially this can be invoked from multiple threads
    @Synchronized
    override fun updateReceived(status: FlowStatus) {
        logger.info(
            "Listener $id received flow status update: ${status.flowStatus.name} (clientRequestId: $clientRequestId, " +
                    "holdingId: ${holdingIdentity.toCorda().shortHash})."
        )

        onUpdate(status)
    }

    override fun close(message: String) {
        onCloseCallback(message)
    }

    override fun close() {
        close("Listener closed.")
    }
}