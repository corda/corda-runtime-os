package net.corda.flow.fiber.cache

import net.corda.data.flow.FlowKey
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowFiberImpl

/**
 * Cache for flow fibers.
 */
interface FlowFiberCache {
    /**
     * Put a flow fiber into the cache keyed by the given flowId.
     */
    fun put(key: FlowKey, fiber: FlowFiberImpl)

    /**
     * Get a flow fiber from the cache with the given flowId, or else return null.
     */
    fun get(key: FlowKey): FlowFiberImpl?

    /**
     * Invalidate and remove a flow fiber from the cache with the give flow identifier.
     */
    fun remove(key: FlowKey)

    /**
     * Invalidate and remove flow fiber from the cache with the give flow identifiers.
     */
    fun remove(keys: Collection<FlowKey>)

    /**
     * Invalidate and remove all flow fibers from the cache for the given holding identity.
     */
    fun remove(holdingIdentity: HoldingIdentity)
}
