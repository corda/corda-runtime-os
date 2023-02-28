package net.corda.crypto.cipher.suite

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.exceptions.CryptoException


/**
 * Signals that there is a throttling by a downstream service, such as HSM or any other and provides parameters which
 * can be used to retry the operation which caused that.
 */
class CryptoThrottlingException : CryptoException {
    companion object {
        /**
         * Creates an instance of the exception with the message
         * and default exponential backoff of 6 max attempts with 1s, 2s, 4s, 8s and 16s wait time in between.
         */
        @JvmStatic
        fun createExponential(message: String): CryptoThrottlingException =
            CryptoThrottlingException(message, null, createExponentialBackoff())

        /**
         * Creates an instance of the exception with the message, cause
         * and default exponential backoff of 6 max attempts with 1s, 2s, 4s, 8s and 16s wait time in between.
         */
        @JvmStatic
        fun createExponential(message: String, cause: Throwable?): CryptoThrottlingException =
            CryptoThrottlingException(message, cause, createExponentialBackoff())

        /**
         * Creates an instance of the exception with the message and customized exponential backoff.
         */
        @JvmStatic
        fun createExponential(message: String, maxAttempts: Int, initialBackoff: Long): CryptoThrottlingException =
            CryptoThrottlingException(message, null, createExponentialBackoff(maxAttempts, initialBackoff))

        /**
         * Creates an instance of the exception with the message, cause and customized exponential backoff.
         */
        @JvmStatic
        fun createExponential(
            message: String,
            cause: Throwable?,
            maxAttempts: Int,
            initialBackoff: Long
        ): CryptoThrottlingException =
            CryptoThrottlingException(message, cause, createExponentialBackoff(maxAttempts, initialBackoff))

        private fun createLinearBackoff(): List<Long> =
            createBackoff(3, listOf(200L))

        private fun createExponentialBackoff(): List<Long> =
            createExponentialBackoff(6, 1000L)

        private fun createExponentialBackoff(maxAttempts: Int, initialBackoff: Long): List<Long> = when {
            maxAttempts <= 1 -> emptyList()
            else -> {
                var next = initialBackoff
                List(maxAttempts - 1) {
                    val current = next
                    next *= 2
                    current
                }
            }
        }

        private fun createBackoff(maxAttempts: Int, backoff: List<Long>): List<Long> = when {
            maxAttempts <= 1 -> emptyList()
            backoff.isEmpty() -> createBackoff(maxAttempts, listOf(0L))
            else -> List(maxAttempts - 1) {
                    if (it < backoff.size) {
                        backoff[it]
                    } else {
                        backoff[backoff.size - 1]
                    }
                }
        }
    }

    private val backoff: List<Long>

    /**
     * Creates an instance of the exception with the message
     * and linear backoff of 3 max attempts with 200 milliseconds wait time in between.
     */
    constructor(message: String) : super(message,true) {
        this.backoff = createLinearBackoff()
    }

    /**
     * Creates an instance of the exception with the message
     * and specified backoff. The number of max attempts would be the size of the array plus 1.
     */
    constructor(message: String, backoff: List<Long>) : super(message, true) {
        this.backoff = backoff
    }

    /**
     * Creates an instance of the exception with the message, cause
     * and linear backoff of 3 max attempts with 200 milliseconds wait time in between.
     */
    constructor(message: String, cause: Throwable?) : super(message, true, cause) {
        this.backoff = createLinearBackoff()
    }

    /**
     * Creates an instance of the exception with the message, cause, and specified backoff.
     * The number of max attempts would be the size of the array plus 1.
     */
    constructor(message: String, cause: Throwable?, backoff: List<Long>) : super(message, true, cause) {
        this.backoff = backoff
    }

    fun getBackoff(attempt: Int): Long =
        if (attempt < 1 || attempt > backoff.size) {
            -1
        } else {
            backoff[attempt - 1]
        }
}