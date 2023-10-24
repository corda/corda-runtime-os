package net.corda.flow.fiber.cache

import net.corda.data.flow.FlowKey
import net.corda.flow.fiber.FlowFiberImpl
import net.corda.sandboxgroupcontext.SandboxedCache

/**
 * Cache for flow fibers.
 */
interface FlowFiberCache: SandboxedCache {
    /**
     * Put a flow fiber into the cache keyed by the given [FlowKey].
     */
    fun put(key: FlowKey, fiber: FlowFiberImpl)

    /**
     * Get a flow fiber from the cache with the given [FlowKey], or else return null.
     */
    fun get(key: FlowKey): FlowFiberImpl?

    /**
     * Invalidate and remove a flow fiber from the cache with the given [FlowKey].
     */
    fun remove(key: FlowKey)
}
