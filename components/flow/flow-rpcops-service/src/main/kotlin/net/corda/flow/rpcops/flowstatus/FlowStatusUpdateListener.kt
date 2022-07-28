package net.corda.flow.rpcops.flowstatus

import java.util.UUID
import net.corda.data.flow.output.FlowStatus

/**
 * Handle flow status updates.
 */
interface FlowStatusUpdateListener : AutoCloseable {

    val id: UUID

    // todo conal - not sure if FlowStatus Avro type is correct on this API.
    /**
     * Update received for flow status.
     */
    fun updateReceived(status: FlowStatus)
}