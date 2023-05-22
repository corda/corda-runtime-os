package net.corda.flow.fiber.metrics

import io.micrometer.core.instrument.Timer
import net.corda.v5.base.annotations.Suspendable
import java.time.Duration
import java.time.Instant

@Suspendable
fun <T> recordSuspendable(timer: () -> Timer, operation: () -> T): T {
    val startTime = Instant.now()
    return try {
        operation()
    } finally {
        timer().record(Duration.between(startTime, Instant.now()))
    }
}
