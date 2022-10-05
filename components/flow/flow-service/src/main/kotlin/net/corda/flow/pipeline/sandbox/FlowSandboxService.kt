package net.corda.flow.pipeline.sandbox

import net.corda.virtualnode.HoldingIdentity

interface FlowSandboxService {

    /**
     * Validate a virtual node is not in a maintenance state.
     *
     * @throws IllegalStateException if the virtual node is in a maintenance state.
     */
    fun validateVirtualNodeMaintenance(holdingIdentity: HoldingIdentity)

    /**
     * Get a virtual node's sandbox context.
     */
    fun get(holdingIdentity: HoldingIdentity): FlowSandboxGroupContext
}
