package net.corda.flow.fiber.cache

import net.corda.flow.fiber.FlowFiberImpl
import net.corda.virtualnode.HoldingIdentity

/**
 * Cache for flow fibers.
 */
interface FlowFiberCache {
    /**
     * Put a flow fiber into the cache keyed by the given flowId.
     */
    fun put(key: FlowFiberCacheKey, fiber: FlowFiberImpl)

    /**
     * Get a flow fiber from the cache with the given flowId, or else return null.
     */
    fun get(key: FlowFiberCacheKey): FlowFiberImpl?

    /**
     * Invalidate and remove a flow fiber from the cache with the give flow identifier.
     */
    fun remove(key: FlowFiberCacheKey)

    /**
     * Invalidate and remove all flow fibers from the cache for the given holding identities.
     */
    fun remove(holdingIdentities: Set<HoldingIdentity>)
}
