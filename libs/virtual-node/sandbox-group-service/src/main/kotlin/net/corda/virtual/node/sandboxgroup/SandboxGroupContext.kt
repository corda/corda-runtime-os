package net.corda.virtual.node.sandboxgroup

import net.corda.sandbox.SandboxGroup
import net.corda.virtual.node.context.VirtualNodeContext

/**
 *  The [AutoCloseable] object should be called to clean up and remove
 *  the virtual node from the current process.
 */
data class SandboxGroupContext (
    val context: VirtualNodeContext,
    val sandboxGroup: SandboxGroup,
    val closeable: AutoCloseable
)
