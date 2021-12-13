package net.corda.virtualnode.sandboxgroup

import net.corda.sandbox.SandboxGroup

/**
 * A context object that is essentially a decorated [SandboxGroup]
 *
 * This should contain references to all appropriate information for a given CPI, either here,
 * or more likely in [VirtualNodeInfo].
 */
interface SandboxGroupContext : AutoCloseable {
    val context: VirtualNodeContext
    val sandboxGroup: SandboxGroup

    /**
     * Get an object into the `SandboxGroupContext` instance's cache using the given key.
     *
     * Returns null if it doesn't exist
     *
     * Throws [TypeCastException] if the object cannot be cast to the expected type.
     */
    fun <T> get(key: String) : T?
}
