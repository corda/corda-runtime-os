package net.corda.sandboxgroupcontext

/**
 * An interface to provide steps to close a [SandboxGroupContext] with an [onInvalidate] step which is called
 * before close when a sandbox is invalidated.
 *
 * Implementing this interface allows the implementation to define behaviour when a [SandboxGroupContext] is
 * closed and optionally additional behaviour before a [SandboxGroupContext] is closed.
 *
 * For example, [onInvalidate] could be used to remove hard references to the sandbox before it is moved to
 * an expiry queue.
 */
interface SandboxCloseable : AutoCloseable {

    /**
     * Optional additional step executed before a [SandboxGroupContext] is moved to an expiry queue or closed.
     *
     * @param virtualNodeContext the [VirtualNodeContext] associated with the sandbox to be closed.
     */
    fun onInvalidate(virtualNodeContext: VirtualNodeContext) {  }
}