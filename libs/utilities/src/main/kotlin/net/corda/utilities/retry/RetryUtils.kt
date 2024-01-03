@file:JvmName("RetryUtils")

package net.corda.utilities.retry

import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.Logger

/**
 * Attempts to execute the provided [operation] and returns the result if no errors occur.
 * If an error is thrown from [operation] and the [recoverable] function determines that the exception is not retryable
 * at the moment, the original exception is thrown.
 * If an error is thrown from [operation] the [recoverable] function determines that the exception is retryable, though,
 * the [backoffStrategy] is employed to determine delay intervals for retrying the operation. This process continues
 * until [operation] succeeds, or up to a maximum of [maxRetries] or [maxTimeMillis], whichever happens first.
 *
 *  @param logger Logger instance used to register messages, if any.
 *  @param maxRetries Maximum amount of retries to spend across all retries before giving up. A zero or negative value
 *      prevents retries.
 *  @param maxTimeMillis Maximum amount of time to spend across all retries before giving up. A zero or negative value
 *      prevents retries.
 *  @param backoffStrategy Strategy to use when calculating next delay interval. The retry process is halted if the
 *      returned delay is negative.
 *  @param recoverable A function to determine whether the exception can be automatically retried or not based on the
 *   attempt number, amount of milliseconds elapsed since the first attempt and the Throwable itself.
 *  @param exceptionProvider A function that creates and returns a [CordaRuntimeException] based on the provided error
 *   message and throwable ([RetryException] is used by default).
 *  @param operation Block to execute.
 *  @throws RetryException, or the exception type created through [exceptionProvider], if the original exception was
 *   marked as retryable but all retry attempts failed.
 */
@Suppress("LongParameterList")
fun <T> tryWithBackoff(
    logger: Logger,
    maxRetries: Long,
    maxTimeMillis: Long,
    backoffStrategy: BackoffStrategy,
    recoverable: (Int, Long, Throwable) -> Boolean = { _, _, _ -> false },
    exceptionProvider: (String, Throwable) -> CordaRuntimeException = { m, t -> RetryException(m, t) },
    operation: () -> T,
): T {
    var attempt = 1
    var originalException: Throwable?
    val startTime = System.currentTimeMillis()

    while (true) {
        try {
            return operation()
        } catch (throwable: Throwable) {
            val elapsedMillis = System.currentTimeMillis() - startTime
            if (!recoverable(attempt, elapsedMillis, throwable) || (maxRetries <= 0) || (maxTimeMillis <= 0)) {
                throw throwable
            }

            originalException = throwable
            val delayMillis = backoffStrategy.delay(attempt)

            if ((delayMillis < 0) || (attempt >= maxRetries) || ((elapsedMillis + delayMillis) >= maxTimeMillis)) {
                ("Execution failed with \"${originalException.message}\" after retrying $attempt times for " +
                    "$elapsedMillis milliseconds.").also {
                    logger.warn(it, originalException)
                    throw exceptionProvider(it, originalException)
                }
            }

            attempt++
            logger.warn(
                "Execution failed on attempt $attempt, will retry after $delayMillis milliseconds",
                originalException
            )

            Thread.sleep(delayMillis)
        }
    }
}
