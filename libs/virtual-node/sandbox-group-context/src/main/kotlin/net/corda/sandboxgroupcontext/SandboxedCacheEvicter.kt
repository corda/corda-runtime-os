package net.corda.sandboxgroupcontext

/**
 * [SandboxedCacheEvicter] links [SandboxedCache]s to listen to for the eviction of sandboxes from the sandbox cache.
 *
 * [SandboxedCacheEvicter] should be injected into processors via their sandbox services (e.g. FlowSandboxService, EntitySandboxService and
 * VerificationSandboxService) and [setSandboxGroupType] called to specify the sandbox type that the [SandboxedCacheEvicter] is linked to.
 */
interface SandboxedCacheEvicter {

    /**
     * Set the [SandboxGroupType] that the [SandboxedCacheEvicter] cleans up after.
     *
     * @param sandboxGroupType The [SandboxGroupType] of the evicter.
     */
    fun setSandboxGroupType(sandboxGroupType: SandboxGroupType)
}