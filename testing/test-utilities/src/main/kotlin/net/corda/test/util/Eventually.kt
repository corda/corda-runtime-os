package net.corda.test.util

import java.time.Duration

/**
 * Ideas borrowed from "io.kotlintest" with some improvements made
 * This is meant for use from Kotlin code use only mainly due to it's inline/reified nature
 *
 * @param duration How long to wait for, before returning the last test failure. The default is 5 seconds.
 * @param waitBetween How long to wait before retrying the test condition. The default is 1/10th of a second.
 * @param waitBefore How long to wait before trying the test condition for the first time. It's assumed that [eventually]
 * is being used because the condition is not _immediately_ fulfilled, so this defaults to the value of [waitBetween].
 * @param allowAllExceptions if true all exceptions will be caught for a retry. If false only assertion
 *   exception will be caught.
 * @param test A test which should pass within the given [duration].
 *
 * @throws AssertionError, if the test does not pass within the given [duration].
 */
fun <R> eventually(
    duration: Duration = Duration.ofSeconds(5),
    waitBetween: Duration = Duration.ofMillis(100),
    waitBefore: Duration = waitBetween,
    allowAllExceptions: Boolean = false,
    test: () -> R,
): R {
    val exceptionHandler = ExceptionHandler(
        duration,
        waitBetween,
        allowAllExceptions,
    )
    if (!waitBefore.isZero) Thread.sleep(waitBefore.toMillis())

    while (true) {
        try {
            return test()
        } catch (e: Throwable) {
            exceptionHandler.handleException(e)
        }
    }
}
private class ExceptionHandler(
    duration: Duration,
    val waitBetween: Duration,
    val allowAllExceptions: Boolean
) {
    private var times = 0
    private val end = System.nanoTime() + duration.toNanos()
    fun handleException(e: Throwable) {
        if (System.nanoTime() > end) {
            throw AssertionError(
                "Test failed with \"${e.message}\" after duration; attempted $times times",
                e,
            )
        }
        if ((e is AssertionError) || (allowAllExceptions)) {
            if (!waitBetween.isZero) {
                Thread.sleep(waitBetween.toMillis())
                times++
            }
        } else {
            throw e
        }
    }
}
