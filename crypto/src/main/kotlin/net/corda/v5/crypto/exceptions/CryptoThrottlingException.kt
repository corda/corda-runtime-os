package net.corda.v5.crypto.exceptions

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.BackoffStrategy
import net.corda.v5.base.exceptions.BackoffStrategy.Companion.createExponentialBackoff
import net.corda.v5.base.exceptions.BackoffStrategy.Companion.createLinearBackoff

/**
 * Signals that there is a throttling by a downstream service, such as HSM or any other and provides parameters which
 * can be used to retry the operation which caused that.
 */
@CordaSerializable
open class CryptoThrottlingException : CryptoException, BackoffStrategy {
    companion object {
        /**
         * Creates an instance of the exception with the message
         * and default exponential backoff of 6 max attempts with 1s, 2s, 4s, 8s and 16s wait time in between
         */
        @JvmStatic
        fun createExponential(message: String): CryptoThrottlingException =
            CryptoThrottlingException(message, null, createExponentialBackoff())

        /**
         * Creates an instance of the exception with the message, cause
         * and default exponential backoff of 6 max attempts with 1s, 2s, 4s, 8s and 16s wait time in between
         */
        @JvmStatic
        fun createExponential(message: String, cause: Throwable?): CryptoThrottlingException =
            CryptoThrottlingException(message, cause, createExponentialBackoff())

        /**
         * Creates an instance of the exception with the message and customized exponential backoff
         */
        @JvmStatic
        fun createExponential(message: String, maxAttempts: Int, initialBackoff: Long): CryptoThrottlingException =
            CryptoThrottlingException(message, null, createExponentialBackoff(maxAttempts, initialBackoff))

        /**
         * Creates an instance of the exception with the message, cause and customized exponential backoff
         */
        @JvmStatic
        fun createExponential(
            message: String,
            cause: Throwable?,
            maxAttempts: Int,
            initialBackoff: Long
        ): CryptoThrottlingException =
            CryptoThrottlingException(message, cause, createExponentialBackoff(maxAttempts, initialBackoff))
    }

    private val strategy: BackoffStrategy

    /**
     * Creates an instance of the exception with the message
     * and linear backoff of 3 max attempts with 200 milliseconds wait time in between
     */
    constructor(message: String) : super(message, true) {
        strategy = createLinearBackoff()
    }

    /**
     * Creates an instance of the exception with the message
     * and specified backoff, the number of max attempts would be the size of the array plus 1
     */
    constructor(message: String, vararg backoff: Long) : super(message, true) {
        strategy = BackoffStrategy.Default(backoff.toTypedArray())
    }

    /**
     * Creates an instance of the exception with the message, cause
     * and linear backoff of 3 max attempts with 200 milliseconds wait time in between
     */
    constructor(message: String, cause: Throwable?) : super(message, true, cause) {
        strategy = createLinearBackoff()
    }

    /**
     * Creates an instance of the exception with the message, cause
     * and specified backoff, the number of max attempts would be the size of the array plus 1
     */
    constructor(message: String, cause: Throwable?, vararg backoff: Long) : super(message, true, cause) {
        strategy = BackoffStrategy.Default(backoff.toTypedArray())
    }

    private constructor(message: String, cause: Throwable?, strategy: BackoffStrategy) : super(
        message,
        true,
        cause
    ) {
        this.strategy = strategy
    }

    override fun getBackoff(attempt: Int): Long =
        strategy.getBackoff(attempt)
}