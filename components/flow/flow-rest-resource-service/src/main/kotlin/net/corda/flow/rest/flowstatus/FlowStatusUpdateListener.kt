package net.corda.flow.rest.flowstatus

import net.corda.data.flow.output.FlowStatus

/**
 * Handle flow status updates.
 */
interface FlowStatusUpdateListener : AutoCloseable {

    /**
     * Identifier for this listener.
     */
    val id: String

    /**
     * Update received for flow status.
     */
    fun updateReceived(status: FlowStatus)

    /**
     * Close with a message.
     */
    fun close(message: String)
}