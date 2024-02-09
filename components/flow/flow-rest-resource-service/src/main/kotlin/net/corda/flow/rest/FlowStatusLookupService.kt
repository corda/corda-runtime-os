package net.corda.flow.rest

import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

interface FlowStatusLookupService: Lifecycle {

    /**
     * Initialise the [FlowStatusLookupService], starting the subscriptions and provisioning a StateManager.
     *
     * @param messagingConfig A [SmartConfig] containing messaging configuration.
     * @param stateManagerConfig A [SmartConfig] containing StateManager connection information.
     * @param restConfig A [SmartConfig] containing REST configuration, including the time-to-live for stale statuses.
     */
    fun initialise(messagingConfig: SmartConfig, stateManagerConfig: SmartConfig, restConfig: SmartConfig)

    /**
     * Returns the [FlowStatus] associated with a client request ID and [HoldingIdentity], which together
     * uniquely identify a running flow. If no [FlowStatus] can be found for this combination of request ID
     * and [HoldingIdentity], a null value will be returned.
     *
     * @param clientRequestId The client request ID which was provided when starting the flow.
     * @param holdingIdentity The [HoldingIdentity] that the flow belongs to.
     * @return Latest [FlowStatus] for this flow, or null if non can be found.
     * */
    fun getStatus(clientRequestId: String, holdingIdentity: HoldingIdentity): FlowStatus?

    /**
     * Returns a list of all flow statuses for a given holding identity. If none are found,
     * an empty list will be returned.
     *
     * @param holdingIdentity The [HoldingIdentity] which you'd like to retrieve flow statuses for.
     * @return A list of flow statuses, or an empty list if none exist.
     * */
    fun getStatusesPerIdentity(holdingIdentity: HoldingIdentity): List<FlowStatus>
}
