package net.corda.sandboxgroupcontext

/**
 * Instances of [SandboxedCache] are "sandbox-level" caches.
 *
 * These caches should exist as global OSGi singletons.
 *
 * [SandboxedCache]s have invalidate keys related to a sandbox when that sandbox is evicted from
 * the worker's sandbox cache. [remove] is called when this occurs.
 *
 * All [SandboxedCache]s should contain an internal cache that contains _at least_ the [VirtualNodeContext] of
 * the sandbox that put key-value pairs into the cache.
 *
 * Ideally [CacheKey] should be used by a [SandboxedCache]'s internal cache.
 */
interface SandboxedCache {

    /**
     * A cache key holding sandbox information and the _real_ key to the cache.
     *
     * @property virtualNodeContext The [VirtualNodeContext] of the sandbox that added the key-value pair.
     * @property key The real key of the cache.
     */
    data class CacheKey<T>(val virtualNodeContext: VirtualNodeContext, val key: T)

    /**
     * Removes key-value pairs from the cache based on [VirtualNodeContext].
     *
     * Implementations of this method should iterate over the keys to remove the ones that match the [virtualNodeContext].
     *
     * @param virtualNodeContext The [VirtualNodeContext] of the keys to remove from the cache.
     */
    fun remove(virtualNodeContext: VirtualNodeContext)

}