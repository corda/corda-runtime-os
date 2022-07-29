package net.corda.flow.rpcops

import java.util.UUID
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.rpcops.flowstatus.FlowStatusUpdateListener
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
     */
    fun registerFlowStatusFeed(clientRequestId: String, holdingIdentity: HoldingIdentity, listener: FlowStatusUpdateListener)

    /**
     * Unregisters the flow status feed for the given [clientRequestId] and [holdingIdentity].
     */
    fun unregisterFlowStatusFeed(listenerId: UUID)

}