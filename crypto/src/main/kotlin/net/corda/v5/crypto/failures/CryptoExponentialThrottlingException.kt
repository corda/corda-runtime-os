package net.corda.v5.crypto.failures

import net.corda.v5.base.annotations.CordaSerializable

/**
 * Signals that there is a throttling by a downstream service, such as HSM or any other and the thrower wishes
 * to execute exponential backoff of the condition.
 */
@CordaSerializable
open class CryptoExponentialThrottlingException : CryptoThrottlingException {
    private val backoffStrategy: CryptoExponentialRetryStrategy

    constructor(message: String) : super(message) {
        backoffStrategy = CryptoExponentialRetryStrategy()
    }

    constructor(message: String, cause: Throwable?) : super(message, cause) {
        backoffStrategy = CryptoExponentialRetryStrategy()
    }

    constructor(
        message: String,
        initialBackoff: Long,
        backoffMultiplier: Long,
        maxAttempts: Int,
        cause: Throwable?
    ) : super(message, cause) {
        backoffStrategy = CryptoExponentialRetryStrategy(
            maxAttempts = maxAttempts,
            initialBackoff = initialBackoff,
            backoffMultiplier = backoffMultiplier
        )
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
        backoffStrategy.getBackoff(attempt, currentBackoffMillis)
}