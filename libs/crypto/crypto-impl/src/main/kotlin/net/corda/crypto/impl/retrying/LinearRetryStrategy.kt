package net.corda.crypto.impl.retrying

import net.corda.v5.crypto.failures.CryptoRetryStrategy
import java.time.Duration

/**
 * Linear retry strategy.
 *
 * @property maxAttempts the maximum number of attempts
 * @property initialBackoff the initial backoff time after the first failure
 * @property backoff the backoff time after all others failures
 */
class LinearRetryStrategy(
    maxAttempts: Int = 6,
    initialBackoff: Duration = Duration.ofMillis(100),
    backoff: Duration = initialBackoff
) : CryptoRetryStrategy {
    private val maxAttempts = Integer.max(maxAttempts, 1)
    private val initialBackoff = initialBackoff.toMillis()
    private val backoff = backoff.toMillis()

    override fun getBackoff(attempt: Int, currentBackoffMillis: Long): Long =
        when {
            attempt < 1 || attempt >= maxAttempts -> -1
            attempt == 1 -> initialBackoff
            else -> backoff
        }
}