package net.corda.httprpc.durablestream

import net.corda.httprpc.security.rpcContext
import net.corda.httprpc.durablestream.api.Cursor
import net.corda.httprpc.durablestream.api.FiniteDurableCursorBuilder

/**
 * A set of helper method to make working with durable streams constructs easier
 */
object DurableStreamHelper {

    data class DurableStreamContextExecutionOutcome<T>(
        val positionedValues: List<Cursor.PollResult.PositionedValue<T>>,
        val remainingElementsCountEstimate: Long?,
        val isLastResult: Boolean
    )

    /**
     * DSL to unify retrieval of [DurableStreamContext]
     *
     * @param block function that performs some processing with [DurableStreamContext]
     * and returns a result in the form of a [DurableStreamContextExecutionOutcome]
     */
    @JvmStatic
    fun <T> withDurableStreamContext
            (block: DurableStreamContext.() -> DurableStreamContextExecutionOutcome<T>): FiniteDurableCursorBuilder<T> {
        val durableStreamContext = requireNotNull(rpcContext()?.invocation?.durableStreamContext) {
            "Durable stream context should always be set for durable streams invocation."
        }
        val (positionedValues, remainingElementsCountEstimate, isLastResult) = block(durableStreamContext)
        return DurableCursorTransferObject(pollResult(positionedValues, remainingElementsCountEstimate, isLastResult))
    }

    @JvmStatic
    fun <T> positionedValue(value: T, position: Long): Cursor.PollResult.PositionedValue<T> {
        return DurableCursorTransferObject.Companion.PositionedValueImpl(value, position)
    }

    @JvmStatic
    fun <T> pollResult(
        positionedValues: List<Cursor.PollResult.PositionedValue<T>>,
        remainingElementsCountEstimate: Long?,
        isLastResult: Boolean
    ): Cursor.PollResult<T> {
        return DurableCursorTransferObject.Companion.PollResultImpl(positionedValues, remainingElementsCountEstimate, isLastResult)
    }

    @JvmStatic
    fun <T> outcome(
        positionedValues: List<Cursor.PollResult.PositionedValue<T>>,
        remainingElementsCountEstimate: Long?,
        isLastResult: Boolean
    ): DurableStreamContextExecutionOutcome<T> =
        DurableStreamContextExecutionOutcome(positionedValues, remainingElementsCountEstimate, isLastResult)

    @JvmStatic
    fun <T> outcome(
        remainingElementsCountEstimate: Long?,
        isLastResult: Boolean,
        positionedValues: List<Pair<Long, T>>
    ): DurableStreamContextExecutionOutcome<T> =
        DurableStreamContextExecutionOutcome(
            positionedValues.map { positionedValue(it.second, it.first) },
            remainingElementsCountEstimate,
            isLastResult
        )
}