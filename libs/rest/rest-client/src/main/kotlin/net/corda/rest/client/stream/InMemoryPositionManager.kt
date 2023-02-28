package net.corda.rest.client.stream

import net.corda.rest.durablestream.api.PositionManager
import java.util.concurrent.atomic.AtomicLong

/**
 * Simple implementation of [PositionManager] that holds [net.corda.rest.durablestream.api.DurableCursor] position in memory.
 */
class InMemoryPositionManager : PositionManager {

    private val positionHolder = AtomicLong(PositionManager.MIN_POSITION)

    override fun get(): Long = positionHolder.get()

    override fun accept(inLong: Long) = positionHolder.set(inLong)
}