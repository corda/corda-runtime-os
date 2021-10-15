package net.corda.httprpc.client.stream

import net.corda.v5.base.stream.PositionManager
import java.util.concurrent.atomic.AtomicLong

/**
 * Simple implementation of [PositionManager] that holds [net.corda.v5.base.stream.DurableCursor] position in memory.
 */
class InMemoryPositionManager : PositionManager {

    private val positionHolder = AtomicLong(PositionManager.MIN_POSITION)

    override fun get(): Long = positionHolder.get()

    override fun accept(inLong: Long) = positionHolder.set(inLong)
}