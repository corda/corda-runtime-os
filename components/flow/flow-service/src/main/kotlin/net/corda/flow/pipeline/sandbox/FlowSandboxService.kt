package net.corda.flow.pipeline.sandbox

import net.corda.virtualnode.HoldingIdentity

interface FlowSandboxService {

    /**
     * Get a virtual node's sandbox context ensuring the virtual node is not in any maintenance states.
     *
     * @throws IllegalStateException if the virtual node is not in an active state.
     */
    fun getWithVNodeMaintenanceValidation(holdingIdentity: HoldingIdentity): FlowSandboxGroupContext

    /**
     * Get a virtual node's sandbox context.
     */
    fun get(holdingIdentity: HoldingIdentity): FlowSandboxGroupContext
}
