package net.corda.v5.crypto.failures

/**
 * Strategy to handle transient faults by retrying.
 */
fun interface CryptoRetryStrategy {
    /**
     * Returns the next wait period in milliseconds for the given attempt and current waiting period.
     * The return value of -1 would mean that the operations is deemed unrecoverable so no further attempts to retry.
     */
    fun getBackoff(attempt: Int, currentBackoffMillis: Long): Long
}