package net.corda.libs.statemanager.api

import java.time.Instant

/**
 * Interval of time to use when filtering by [State.modifiedTime].
 */
data class IntervalFilter(val start: Instant, val finish: Instant)

/**
 * Supported comparison operations over [State.metadata] values.
 */
enum class Operation { Equals, NotEquals, LesserThan, GreaterThan }

/**
 * Parameters to use when filtering by [State.metadata] keys and values.
 * As with the [State.metadata] itself, [key] can only be a [String] and [value] can only be of a primitive type.
 */
data class MetadataFilter(val key: String, val operation: Operation, val value: Any)
