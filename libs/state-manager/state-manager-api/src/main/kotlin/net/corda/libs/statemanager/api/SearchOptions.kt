package net.corda.libs.statemanager.api

import java.time.Instant

/**
 * Supported comparison operations on [State.metadata] values.
 */
enum class Operation {
    Equals,
    NotEquals,
    LesserThan,
    GreaterThan,
}

/**
 * Interval of time to use when filtering by [State.modifiedTime] within the underlying persistent storage.
 */
data class IntervalFilter(val start: Instant, val finish: Instant)

/**
 * Parameters to use when filtering by [State.metadata] keys and values within the underlying persistent storage.
 * As with the [State.metadata] itself, [key] can only be a [String] and [value] can only be of a primitive type.
 */
data class SingleKeyFilter(val key: String, val operation: Operation, val value: Any)
