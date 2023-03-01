package net.corda.flow.rest

import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.rest.flowstatus.FlowStatusUpdateListener
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

interface FlowStatusCacheService: Lifecycle {

    /**
     * Initialises the API implementation. This method may be called multiple times throughout the life
     * of the API.
     */
    fun initialise(config: SmartConfig)

    fun getStatus(clientRequestId: String, holdingIdentity: HoldingIdentity): FlowStatus?

    fun getStatusesPerIdentity(holdingIdentity: HoldingIdentity): List<FlowStatus>

    /**
     * Register the provided flow status update handler to handle the receiving of updates for a flow identified by the [clientRequestId]
     * and [holdingIdentity].
     *
     * @return [AutoCloseable] registration object for closing and removing the flow status listener.
     */
    fun registerFlowStatusListener(clientRequestId: String, holdingIdentity: HoldingIdentity, listener: FlowStatusUpdateListener):
            AutoCloseable

}