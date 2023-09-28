package net.corda.libs.statemanager.api

import java.time.Instant

/**
 * Supported comparison operations on metadata values.
 */
enum class Operation {
    Equals,
    NotEquals,
    LesserThan,
    GreaterThan,
}

data class IntervalFilter(val start: Instant, val finish: Instant)

data class SingleKeyFilter(val key: String, val operation: Operation, val value: Any)
