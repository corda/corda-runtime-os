package net.corda.ledger.verification.processor

fun interface VerificationExceptionCategorizer {
    fun categorize(exception: Throwable): VerificationErrorType
}

/**
 * Categories of verification error
 */
enum class VerificationErrorType {
    /**
     * Error should immediately terminate processing on the other side.
     */
    FATAL,

    /**
     * Error should be handed back to user code.
     */
    PLATFORM,

    /**
     * Error should be retried indefinitely.
     */
    TRANSIENT,

    /**
     * Error should be retried a fixed number of times. If it continues to fail, it should become fatal.
     */
    RETRYABLE
}
