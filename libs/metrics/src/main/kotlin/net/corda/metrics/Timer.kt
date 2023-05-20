package net.corda.metrics

import io.micrometer.core.instrument.Timer
import java.time.Duration
import java.time.Instant

inline fun <T> Timer.recordInline(operation: () -> T): T {
    val startTime = Instant.now()
    return try {
        operation()
    } finally {
        this.record(Duration.between(startTime, Instant.now()))
    }
}