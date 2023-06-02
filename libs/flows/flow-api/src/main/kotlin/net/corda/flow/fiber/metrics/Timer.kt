package net.corda.flow.fiber.metrics

import io.micrometer.core.instrument.Timer
import net.corda.v5.base.annotations.Suspendable
import java.time.Duration

@Suspendable
fun <T> recordSuspendable(timer: () -> Timer, operation: () -> T): T {
    val startTime = System.nanoTime()
    return try {
        operation()
    } finally {
        timer().record(Duration.ofNanos(System.nanoTime() - startTime))
    }
}
