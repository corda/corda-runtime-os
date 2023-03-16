package net.corda.rest.durablestream.api

import net.corda.v5.base.annotations.DoNotImplement

/**
 * Responsible for building [FiniteDurableCursor], allowing to assign values to mutable properties before method
 * [build] is called.
 */
@DoNotImplement
interface FiniteDurableCursorBuilder<T> : DurableCursorBuilder<T> {

    override fun build(): FiniteDurableCursor<T>
}