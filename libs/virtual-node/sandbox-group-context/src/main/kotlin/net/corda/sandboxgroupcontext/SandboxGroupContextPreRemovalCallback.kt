package net.corda.sandboxgroupcontext

/**
 * Functional interface for providing pre-removal callback functionality before removing a [SandboxGroupContext].
 */
fun interface SandboxGroupContextPreRemovalCallback {
    /**
     * Performs a pre-removal action with the sandbox's virtual node context.
     *
     * This function is called automatically before a [SandboxGroupContext] is removed.
     * The `virtualNodeContext` parameter provides information about the virtual node context of
     * the sandbox being removed.
     *
     * @param virtualNodeContext the context of the sandbox being removed
     */
    fun preSandboxRemoval(virtualNodeContext: VirtualNodeContext)
}
