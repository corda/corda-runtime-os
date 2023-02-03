package net.corda.httprpc.durablestream.api

import net.corda.v5.base.annotations.DoNotImplement

/**
 * Responsible for building [DurableCursor], allowing to assign values to mutable properties before method
 * [build] is called.
 */
@DoNotImplement
interface DurableCursorBuilder<T> {

    /**
     * Allows getting and setting [PositionManager] which will be used for this [DurableCursor].
     */
    var positionManager: PositionManager

    fun build(): DurableCursor<T>
}