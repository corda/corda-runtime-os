package net.corda.v5.crypto.failures

class CryptoExponentialRetryStrategy(
    private val maxAttempts: Int = DEFAULT_THROTTLE_MAX_ATTEMPTS,
    private val initialBackoff: Long = DEFAULT_THROTTLE_INITIAL_BACKOFF,
    private val backoffMultiplier: Long = DEFAULT_THROTTLE_BACKOFF_MULTIPLIER
) : CryptoRetryStrategy {
    companion object {
        const val DEFAULT_THROTTLE_MAX_ATTEMPTS: Int = 6
        const val DEFAULT_THROTTLE_INITIAL_BACKOFF: Long = 1_000L
        const val DEFAULT_THROTTLE_BACKOFF_MULTIPLIER: Long = 2
    }

    /**
     * Returns the next wait period in milliseconds for the given attempt and current waiting period.
     * The return value of -1 would mean that the operations is deemed unrecoverable so no further attempts to retry.
     *
     * If [attempt] is less than zero or more than the configured [maxAttempts] it'll return -1. For the [attempt] is 1
     * then the return value will be the configured [initialBackoff] otherwise it'll
     * return [currentBackoffMillis] * [backoffMultiplier], so the default strategy would be 1s, 2s, 4s, 8s, 16s
     * and then give up.
     */
    override fun getBackoff(attempt: Int, currentBackoffMillis: Long): Long =
        when {
            attempt < 1 || attempt >= maxAttempts -> -1
            attempt == 1 -> initialBackoff
            else -> currentBackoffMillis * backoffMultiplier
        }
}