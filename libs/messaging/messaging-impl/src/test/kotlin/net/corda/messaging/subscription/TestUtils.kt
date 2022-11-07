package net.corda.messaging.subscription

import org.junit.jupiter.api.fail
import java.time.Duration
import java.time.LocalDateTime

/**
 * Wait while the predicate is true, or timeout.
 */
internal fun waitWhile(timeoutSeconds: Long, predicate: () -> Boolean) {
    val startTime = LocalDateTime.now()
    while (predicate()) {
        if (Duration.between(startTime, LocalDateTime.now()).seconds > timeoutSeconds) {
            fail("wait timed out")
        }
        Thread.sleep(10)
    }
}
