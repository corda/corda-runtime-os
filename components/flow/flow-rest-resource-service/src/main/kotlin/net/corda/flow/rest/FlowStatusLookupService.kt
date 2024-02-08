package net.corda.flow.rest

import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

interface FlowStatusLookupService: Lifecycle {

    /**
     * Initialises the API implementation. This method may be called multiple times throughout the life
     * of the API.
     */
    fun initialise(messagingConfig: SmartConfig, stateManagerConfig: SmartConfig, restConfig: SmartConfig)

    fun getStatus(clientRequestId: String, holdingIdentity: HoldingIdentity): FlowStatus?

    fun getStatusesPerIdentity(holdingIdentity: HoldingIdentity): List<FlowStatus>
}
