package net.corda.metrics

import io.micrometer.core.instrument.Timer
import java.time.Duration

/**
 * Updates the statistics kept by the timer with the specified amount if the time to complete the operation is greater than
 * [greaterThanMillis].
 *
 * @param greaterThanMillis The time that [operation] must take longer than to be recorded.
 * @param operation The operation to execute.
 */
inline fun <T> Timer.recordOptionally(greaterThanMillis: Int, operation: () -> T): T {
    val startTime = System.nanoTime()
    return try {
        operation()
    } finally {
        val duration = Duration.ofNanos(System.nanoTime() - startTime)
        if (duration.toMillis() > greaterThanMillis) {
            record(duration)
        }
    }
}
