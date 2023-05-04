package net.corda.sandboxgroupcontext

/**
 * A closeable interface that adds a pre-close step to close a [SandboxGroupContext] with a given [VirtualNodeContext].
 * Implementing classes should provide a way to execute the pre-close step before closing the sandbox.
 */
interface SandboxCloseable : AutoCloseable {

    /**
     * Executes the pre-close step for the sandbox with the given virtual node context.
     * This method gives the caller an opportunity to remove any hard references to the [SandboxGroupContext] before
     * the sandbox's close is called.
     *
     * @param virtualNodeContext the virtual node context associated with the sandbox to be closed.
     */
    fun preClose(virtualNodeContext: VirtualNodeContext)
}