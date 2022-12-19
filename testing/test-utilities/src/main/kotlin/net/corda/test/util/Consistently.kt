package net.corda.test.util

import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Similar to "eventually" but the opposite: execute an assertion that must continuously succeed during a configurable
 * period of time. This is meant for use from Kotlin code use only mainly due to its inline/reified nature
 *
 * @param duration How long to wait for, before returning the last test success. The default is 5 seconds.
 * @param waitBetween How long to wait before retrying the test condition. The default is 1/10th of a second.
 * @param waitBefore How long to wait before trying the test condition for the first time. It's assumed that [consistently]
 * is being used because the condition is not _immediately_ fulfilled, so this defaults to the value of [waitBetween].
 * @param block A test which should continuously pass during the given [duration].
 *
 * @throws AssertionError, if the test does not pass within the given [duration].
 */
fun <R> consistently(
        duration: Duration = Duration.ofSeconds(5),
        waitBetween: Duration = Duration.ofMillis(100),
        waitBefore: Duration = waitBetween,
        block: () -> R): R {
    var lastSuccess: R? = null
    val end = System.nanoTime() + duration.toNanos()
    if (!waitBefore.isZero) Thread.sleep(waitBefore.toMillis())

    while (System.nanoTime() < end) {
        try {
            lastSuccess = block()
            if (!waitBetween.isZero) Thread.sleep(waitBetween.toMillis())
        } catch (error: AssertionError) {
            throw AssertionError("Test failed with \"${error.message}\" after " +
                    "${TimeUnit.NANOSECONDS.toMillis(end - System.nanoTime())}ms", error)
        }
    }

    return lastSuccess!!
}
