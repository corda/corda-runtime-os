package net.corda.flow.fiber.cache

import net.corda.virtualnode.HoldingIdentity

/**
 * Service for evicting flow fibers from the flow fiber cache.
 */
interface FlowFiberCacheEvictionService {
    /**
     * Evict all flow fibers cached for the given holding identity.
     */
    fun evictByHoldingIdentity(holdingIdentity: HoldingIdentity)

    /**
     * Evict all flow fibers with the given keys.
     */
    fun evict(keys: List<FlowFiberCacheKey>)
}