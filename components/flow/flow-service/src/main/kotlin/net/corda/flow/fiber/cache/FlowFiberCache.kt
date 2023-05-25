package net.corda.flow.fiber.cache

import net.corda.data.flow.FlowKey
import net.corda.flow.fiber.FlowFiberImpl

/**
 * Cache for flow fibers.
 */
interface FlowFiberCache {
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

    /**
     * Invalidate and remove flow fiber from the cache with the given [FlowKey]s.
     */
    fun remove(keys: Collection<FlowKey>)
}
