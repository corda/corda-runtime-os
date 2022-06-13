package net.corda.v5.crypto.exceptions

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
open class CSLExponentialThrottlingException : CSLThrottlingException {
    companion object {
        const val DEFAULT_THROTTLE_INITIAL_BACKOFF: Long = 1_000L
        const val DEFAULT_THROTTLE_BACKOFF_MULTIPLIER: Long = 2
        const val DEFAULT_THROTTLE_MAX_ATTEMPTS: Int = 5
    }

    private val initialBackoff: Long
    private val backoffMultiplier: Long
    private val maxAttempts: Int

    constructor(message: String) : super(message) {
        this.initialBackoff = DEFAULT_THROTTLE_INITIAL_BACKOFF
        this.backoffMultiplier = DEFAULT_THROTTLE_BACKOFF_MULTIPLIER
        this.maxAttempts = DEFAULT_THROTTLE_MAX_ATTEMPTS
    }

    constructor(message: String, cause: Throwable?) : super(message, cause) {
        this.initialBackoff = DEFAULT_THROTTLE_INITIAL_BACKOFF
        this.backoffMultiplier = DEFAULT_THROTTLE_BACKOFF_MULTIPLIER
        this.maxAttempts = DEFAULT_THROTTLE_MAX_ATTEMPTS
    }

    constructor(
        message: String,
        initialBackoff: Long,
        backoffMultiplier: Long,
        maxAttempts: Int,
        cause: Throwable?
    ) : super(message, cause) {
        this.initialBackoff = initialBackoff
        this.backoffMultiplier = backoffMultiplier
        this.maxAttempts = maxAttempts
    }

    override fun getBackoff(attempt: Int, currentBackoffMillis: Long): Long =
        if(attempt < 1 || attempt > maxAttempts) {
          -1
        } else if(attempt == 1) {
            initialBackoff
        } else {
            currentBackoffMillis * backoffMultiplier
        }
}