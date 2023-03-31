package net.corda.flow.fiber

import co.paralleluniverse.fibers.Fiber

/**
 * Cache for flow fibers.
 */
interface FlowFiberCache {
    /**
     * Put a flow fiber into the cache keyed by the given flowId.
     */
    fun put(flowId: String, fiber: Fiber<Any>)

    /**
     * Get a flow fiber from the cache with the given flowId, or else return null.
     */
    fun get(flowId: String): Fiber<Any>?

    /**
     * Invalidate and remove a flow fiber from the cache with the give flow identifier.
     */
    fun remove(flowId: String)
}
