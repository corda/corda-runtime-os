package net.corda.sandboxgroupcontext

/**
 * [SandboxGroupContextService] allows the current sandbox's [SandboxGroupContext] to be set, retrieved and removed.
 *
 * [SandboxGroupContextService] relies on a [ThreadLocal] to provide access to the current sandbox wherever this service is used.
 *
 * Generally, few callers should call [setCurrent] and [removeCurrent] and should be reserved for the setup and cleanup code that is
 * run when setting up a new sandbox (or retrieving from one a cache) and cleaning up resources when the thread using the sandbox has
 * completed processing.
 */
interface SandboxGroupContextService {

    /**
     * Sets the current [SandboxGroupContext] that is tied to the executing thread.
     */
    fun setCurrent(sandboxGroupContext: SandboxGroupContext)

    /**
     * Gets the current [SandboxGroupContext] of the executing thread.
     *
     * @return The current [SandboxGroupContext].
     */
    fun getCurrent(): SandboxGroupContext

    /**
     * Removes the current [SandboxGroupContext] from the executing thread.
     */
    fun removeCurrent()
}