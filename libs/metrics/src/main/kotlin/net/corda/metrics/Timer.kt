package net.corda.metrics

import io.micrometer.core.instrument.Timer
import java.time.Duration
import java.time.Instant

/**
 * Updates the statistics kept by the timer with the specified amount if the time to complete the operation is greater than
 * [greaterThanMillis].
 *
 * @param greaterThanMillis The time that [operation] must take longer than to be recorded.
 * @param operation The operation to execute.
 */
inline fun <T> Timer.recordOptionally(greaterThanMillis: Int, operation: () -> T): T {
    val startTime = Instant.now()
    return try {
        operation()
    } finally {
        val duration = Duration.between(startTime, Instant.now())
        if (duration.toMillis() > greaterThanMillis) {
            record(Duration.between(startTime, Instant.now()))
        }
    }
}