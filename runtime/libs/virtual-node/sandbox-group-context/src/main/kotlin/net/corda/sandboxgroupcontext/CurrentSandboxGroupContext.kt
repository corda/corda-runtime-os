package net.corda.sandboxgroupcontext

/**
 * [CurrentSandboxGroupContext] allows the current sandbox's [SandboxGroupContext] to be set, retrieved and removed.
 *
 * [CurrentSandboxGroupContext] relies on a [ThreadLocal] to provide access to the current sandbox wherever this service is used.
 *
 * Generally, few callers should call [set] and [remove]. [set] should be called when a sandbox is created or retrieved from a cache.
 * [remove] should be called when the code requiring the sandbox has executed and the sandbox needs cleaning up.
 */
interface CurrentSandboxGroupContext {

    /**
     * Sets the current [SandboxGroupContext] that is tied to the executing thread.
     */
    fun set(sandboxGroupContext: SandboxGroupContext)

    /**
     * Gets the current [SandboxGroupContext] of the executing thread.
     *
     * @return The current [SandboxGroupContext].
     */
    fun get(): SandboxGroupContext

    /**
     * Removes the current [SandboxGroupContext] from the executing thread.
     */
    fun remove()
}