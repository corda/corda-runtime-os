package net.corda.flow.rpcops.flowstatus

import java.util.UUID
import net.corda.data.flow.output.FlowStatus

/**
 * Handle flow status updates.
 */
interface FlowStatusUpdateListener : AutoCloseable {

    /**
     * Identifier for this listener.
     */
    val id: UUID

    /**
     * Update received for flow status.
     */
    fun updateReceived(status: FlowStatus)
}