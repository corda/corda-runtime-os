@file:JvmName("RetryUtils")

package net.corda.utilities.retry

import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.Logger

/**
 * Attempts to execute the provided [operation] and returns the result if no errors occur.
 * If an error occurs and the [shouldRetry] function classifies the error as non retryable, the original exception is
 * thrown. If an error occurs and the [shouldRetry] function classifies the error as retryable, though, the
 * [backoffStrategy] is used to determine delay intervals for automatically retrying the [operation]. This process
 * continues until [operation] succeeds, or up to a maximum of [maxRetries] or [maxTimeMillis], whichever happens first.
 *
 * The [shouldRetry] callback function is used to determine (based on the attempt number, elapsed time and exception
 * type) whether the error is retryable or not.
 *
 * Functions [onRetryAttempt] and [onRetryExhaustion] can be leveraged by users to control what happens in between
 * attempts and what exception should be thrown if all retry attempts fail.
 *
 *  @param logger Logger instance used to register messages, if any.
 *  @param maxRetries Maximum amount of retries to spend across all retries before giving up. A zero or negative value
 *      prevents retries.
 *  @param maxTimeMillis Maximum amount of time to spend across all retries before giving up. A zero or negative value
 *      prevents retries.
 *  @param backoffStrategy Strategy to use when calculating next delay interval. The retry process is halted if the
 *      returned delay is negative.
 *  @param shouldRetry A function to determine whether the exception can be automatically retried or not based on the
 *      attempt number, amount of milliseconds elapsed since the first attempt and the Throwable itself.
 *  @param onRetryAttempt Callback function to be executed before a retry attempt is made. By default, it simply logs
 *      (using WARN log level) the current attempt number, the delay that will be used before the next retry attempt,
 *      and the exception thrown by the last attempt.
 *  @param onRetryExhaustion Callback function that returns a [CordaRuntimeException] ([RetryException] by default) to
 *      be executed once all retry attempts have been exhausted. By default, it logs (under WARN log level) the total
 *      amount of attempts, total elapsed time and the exception thrown by the last attempt.
 *  @param operation Block to execute.
 *  @throws RetryException, or the exception type created through [onRetryExhaustion], if the original exception was
 *      marked as retryable but all retry attempts failed.
 */
@Suppress("LongParameterList")
fun <T> tryWithBackoff(
    logger: Logger,
    maxRetries: Long,
    maxTimeMillis: Long,
    backoffStrategy: BackoffStrategy,
    shouldRetry: (Int, Long, Throwable) -> Boolean = { _, _, _ -> false },
    onRetryAttempt: (Int, Long, Throwable) -> Unit = { attempt, delayMillis, throwable ->
        logger.warn("Attempt $attempt failed with \"${throwable.message}\", will try again after $delayMillis milliseconds")
    },
    onRetryExhaustion: (Int, Long, Throwable) -> CordaRuntimeException = { attempts, elapsedMillis, throwable ->
        val errorMessage =
            "Execution failed with \"${throwable.message}\" after retrying $attempts times for $elapsedMillis milliseconds."
        logger.warn(errorMessage)
        RetryException(errorMessage, throwable)
    },
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
            if (!shouldRetry(attempt, elapsedMillis, throwable) || (maxRetries <= 0) || (maxTimeMillis <= 0)) {
                throw throwable
            }

            originalException = throwable
            val delayMillis = backoffStrategy.delay(attempt)
            if ((delayMillis < 0) || (attempt >= maxRetries) || ((elapsedMillis + delayMillis) >= maxTimeMillis)) {
                throw onRetryExhaustion(attempt, elapsedMillis, originalException)
            }

            onRetryAttempt(attempt, delayMillis, originalException)
            Thread.sleep(delayMillis)
            attempt++
        }
    }
}
