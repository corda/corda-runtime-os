package net.corda.httprpc.client.stream

import net.corda.httprpc.durablestream.api.PositionManager
import java.util.concurrent.atomic.AtomicLong

/**
 * Simple implementation of [PositionManager] that holds [net.corda.httprpc.durablestream.api.DurableCursor] position in memory.
 */
class InMemoryPositionManager : PositionManager {

    private val positionHolder = AtomicLong(PositionManager.MIN_POSITION)

    override fun get(): Long = positionHolder.get()

    override fun accept(inLong: Long) = positionHolder.set(inLong)
}