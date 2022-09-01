package net.corda.httprpc.durablestream

import net.corda.httprpc.durablestream.api.Cursor
import net.corda.httprpc.durablestream.api.FiniteDurableCursor
import net.corda.httprpc.durablestream.api.FiniteDurableCursorBuilder
import net.corda.httprpc.durablestream.api.PositionManager
import java.util.function.Supplier

/**
 * Implementation of [FiniteDurableCursorBuilder] which is created on the server side to be marshalled back to the client.
 * Methods of [FiniteDurableCursorBuilder] are not meant to be used, it is just a data container that wraps [Cursor.PollResult].
 */
class DurableCursorTransferObject<T>
(private val pollResult: Cursor.PollResult<T>) : FiniteDurableCursorBuilder<T>, Supplier<Cursor.PollResult<T>> {

    companion object {
        data class PollResultImpl<T>(
            override val positionedValues: List<Cursor.PollResult.PositionedValue<T>>,
            override val remainingElementsCountEstimate: Long?,
            override val isLastResult: Boolean
        ) : Cursor.PollResult<T>

        data class PositionedValueImpl<T>(override val value: T, override val position: Long) : Cursor.PollResult.PositionedValue<T>
    }

    override fun get(): Cursor.PollResult<T> = pollResult

    override var positionManager: PositionManager
        get() = throw UnsupportedOperationException("getting position manager not meant to be called")
        @Suppress("unused_parameter")
        set(value) = throw UnsupportedOperationException("setting position manager not meant to be called")

    override fun build(): FiniteDurableCursor<T> {
        throw UnsupportedOperationException("Method 'build()' is not meant to be called")
    }
}