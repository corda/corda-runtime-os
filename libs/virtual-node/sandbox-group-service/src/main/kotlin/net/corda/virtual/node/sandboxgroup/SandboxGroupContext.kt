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
     * Add an AutoCloseable object to an internal collection.
     *
     * When [close()] is called, [close()] is also called on all objects in the internal collection in reverse
     * order of addition.
     */
    fun addAutoCloseable(autoCloseable: AutoCloseable)
}
