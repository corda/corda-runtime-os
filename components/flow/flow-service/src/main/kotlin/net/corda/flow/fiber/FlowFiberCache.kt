package net.corda.flow.fiber

import net.corda.virtualnode.HoldingIdentity

/**
 * Flow fiber cache is keyed by the holding identity and flow ID.
 */
data class FlowFiberCacheKey(
    private val holdingIdentity: HoldingIdentity,
    private val flowId: String
)

/**
 * Cache for flow fibers.
 */
interface FlowFiberCache {
    /**
     * Put a flow fiber into the cache keyed by the given flowId.
     */
    fun put(key: FlowFiberCacheKey, fiber: Any)

    /**
     * Get a flow fiber from the cache with the given flowId, or else return null.
     */
    fun get(key: FlowFiberCacheKey): Any?

    /**
     * Invalidate and remove a flow fiber from the cache with the give flow identifier.
     */
    fun remove(key: FlowFiberCacheKey)
}
