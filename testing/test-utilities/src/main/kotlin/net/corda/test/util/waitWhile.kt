package net.corda.test.util

import java.time.Duration
import java.time.LocalDateTime

/**
 * Wait while the predicate is true. An example use case would be:
 * ```kotlin
 * // Wait until the asynchronous task is complete
 * waitWhile(Duration.ofSeconds(TEST_TIMEOUT_SECONDS)) { asyncTask.isRunning }
 * ```
 *
 * @param duration How long to wait for before throwing. The default is 5 seconds.
 * @param waitBetween How long to wait before retesting the predicate. The default is 1/10th of a second.
 * @param waitBefore How long to wait before trying the predicate for the first time. It's assumed that [waitWhile]
 * is being used because the condition is not _immediately_ fulfilled, so this defaults to the value of [waitBetween].
 * @param predicate A predicate which should return false within the given [duration].
 *
 * @throws AssertionError, if the predicate remains true at the end of [duration].
 */
fun waitWhile(
    duration: Duration = Duration.ofSeconds(5),
    waitBetween: Duration = Duration.ofMillis(100),
    waitBefore: Duration = waitBetween,
    predicate: () -> Boolean) {
    val startTime = LocalDateTime.now()
    if (!waitBefore.isZero) Thread.sleep(waitBefore.toMillis())
    while (predicate()) {
        if (Duration.between(startTime, LocalDateTime.now()) > duration) {
            throw AssertionError("Test failed, predicate remained true after $duration)")
        }
        if (!waitBetween.isZero) Thread.sleep(waitBetween.toMillis())
    }
}
