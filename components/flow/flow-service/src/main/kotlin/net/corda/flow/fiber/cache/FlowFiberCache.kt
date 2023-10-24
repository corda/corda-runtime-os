package net.corda.flow.fiber.cache

import net.corda.data.flow.FlowKey
import net.corda.flow.fiber.FlowFiber
import net.corda.sandboxgroupcontext.SandboxedCache

/**
 * Cache for flow fibers.
 */
interface FlowFiberCache: SandboxedCache {
    /**
     * Put a flow fiber into the cache keyed by the given [FlowKey] and [suspendCount].
     */
    fun put(key: FlowKey, suspendCount: Int, fiber: FlowFiber)

    /**
     * Get a flow fiber from the cache with the given [FlowKey] and [suspendCount], or else return null.
     */
    fun get(key: FlowKey, suspendCount: Int): FlowFiber?

    /**
     * Invalidate and remove a flow fiber from the cache with the given [FlowKey] and [suspendCount].
     */
    fun remove(key: FlowKey, suspendCount: Int)
}
