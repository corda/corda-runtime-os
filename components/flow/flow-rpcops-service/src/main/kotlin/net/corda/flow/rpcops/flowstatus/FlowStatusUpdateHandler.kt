package net.corda.flow.rpcops.flowstatus

import net.corda.data.flow.output.FlowStatus

/**
 * Handle flow status updates.
 */
interface FlowStatusUpdateHandler : AutoCloseable {

    // todo conal - not sure if FlowStatus Avro type is correct on this API.
    /**
     * Update received for flow status.
     */
    fun onFlowStatusUpdate(status: FlowStatus)

    /**
     * Error(s) occurred.
     */
    fun onError(errors: List<String>)
}