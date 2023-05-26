package net.corda.sandboxgroupcontext

import net.corda.virtualnode.HoldingIdentity

/**
 * Instances of [SandboxedCache] are "sandbox-level" caches.
 *
 * These caches should exist as global OSGi singletons.
 *
 * [SandboxedCache]s have invalidate keys related to a sandbox when that sandbox is evicted from the worker's sandbox cache. [remove] is
 * called when this occurs.
 *
 * All [SandboxedCache]s should contain an internal cache that contains _at least_ the [HoldingIdentity] of the sandbox that put key-value
 * pairs into the cache. If a cache is used by two sandboxes within the same worker, then [SandboxGroupType] should be used as well.
 *
 * Ideally [CacheKey] should be used by a [SandboxedCache]'s internal cache.
 */
interface SandboxedCache {

    /**
     * A cache key holding sandbox information and the _real_ key to the cache.
     *
     * @property holdingIdentity The [HoldingIdentity] of the sandbox that added the key-value pair.
     * @property sandboxGroupType The [SandboxGroupType] of the sandbox that added the key-value pair.
     * @property key The real key of the cache.
     */
    data class CacheKey<T>(val holdingIdentity: HoldingIdentity, val sandboxGroupType: SandboxGroupType, val key: T) {

        /**
         * @param virtualNodeContext The [VirtualNodeContext] of the sandbox that added the key-value pair.
         * @param key The real key of the cache.
         */
        constructor(virtualNodeContext: VirtualNodeContext, key: T) : this(
            virtualNodeContext.holdingIdentity,
            virtualNodeContext.sandboxGroupType,
            key
        )
    }

    /**
     * Removes key-value pairs from the cache based on [HoldingIdentity] and [SandboxGroupType].
     *
     * Implementations of this method should iterate over the keys to remove the ones that match the [holdingIdentity] and
     * [sandboxGroupType].
     */
    fun remove(holdingIdentity: HoldingIdentity, sandboxGroupType: SandboxGroupType)

}