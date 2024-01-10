package net.corda.rest.server.impl.apigen.processing.streams

import net.corda.rest.durablestream.api.Cursor

open class DurableReturnResult<T>(
    val positionedValues: List<Cursor.PollResult.PositionedValue<T>>,
    val remainingElementsCountEstimate: Long?
)

class FiniteDurableReturnResult<T>(
    positionedValues: List<Cursor.PollResult.PositionedValue<T>>,
    remainingElementsCountEstimate: Long?,
    val isLastResult: Boolean
) : DurableReturnResult<T>(positionedValues, remainingElementsCountEstimate)