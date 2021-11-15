package net.corda.virtual.node.sandboxgroup

import net.corda.sandbox.SandboxGroup
import net.corda.virtual.node.context.VirtualNodeContext

/**
 * A context object that is essentially a decorated [SandboxGroup]
 *
 * This should contain references to all appropriate information for a given CPI, either here,
 * or more likely in [VirtualNodeContext].
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
