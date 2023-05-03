package net.corda.sandboxgroupcontext

import net.corda.virtualnode.HoldingIdentity

/**
 * Functional interface for providing pre-removal callback functionality before removing a [SandboxGroupContext].
 */
fun interface SandboxGroupContextPreRemovalCallback {
    /**
     * Performs a pre-removal action with the sandbox's holding identity.
     *
     * This function is called automatically before a [SandboxGroupContext] is removed.
     * The `holdingIdentity` parameter provides information about the holding identity who's
     * sandbox is being removed.
     *
     * @param holdingIdentity the holding identity of the sandbox being removed
     */
    fun preSandboxRemoval(holdingIdentity: HoldingIdentity)
}
