@file:JvmName("RetryUtils")

package net.corda.utilities.retry

import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.Logger

/**
 * Attempts to execute the provided [operation] and returns the result if no errors occur.
 * If an error is thrown from [operation] and the type hasn't been explicitly marked as retryable through the
 * [recoverable] function, the original exception is thrown.
 * If an error is thrown from [operation] and the type is marked as retryable through the [recoverable] function, though,
 * the [backoffStrategy] is employed to determine delay intervals for retrying the operation. This process continues
 * until [operation] succeeds, or up to a maximum of [maxRetries] or [maxTimeMillis], whichever happens first.
 *
 *  @param logger Logger instance used to register messages, if any.
 *  @param maxRetries Maximum amount of retries to spend across all retries before giving up.
 *  @param maxTimeMillis Maximum amount of time to spend across all retries before giving up.
 *  @param backoffStrategy Strategy to use when calculating next delay interval.
 *  @param recoverable A function that determines whether the exception can be automatically retried or not.
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
    recoverable: (Throwable) -> Boolean = { _ -> false },
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
            if (!recoverable(throwable) || (maxRetries <= 0) || (maxTimeMillis <= 0)) {
                throw throwable
            }

            originalException = throwable
            val delayMillis = backoffStrategy.delay(attempt)
            val elapsedMillis = System.currentTimeMillis() - startTime

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
