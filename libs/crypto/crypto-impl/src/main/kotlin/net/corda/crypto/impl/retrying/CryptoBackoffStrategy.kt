package net.corda.crypto.impl.retrying

import net.corda.utilities.retry.BackoffStrategy

class CryptoBackoffStrategy(
    maxAttempts: Int,
    inputDelays: List<Long>,
) : BackoffStrategy {
    val maxRetries = maxAttempts.toLong()
    private val delays: Array<Long> = createBackoffArray(maxAttempts, inputDelays)

    private fun createBackoffArray(maxAttempts: Int, backoff: List<Long>): Array<Long> = when {
        maxAttempts <= 1 -> emptyArray()
        backoff.isEmpty() -> createBackoffArray(maxAttempts, listOf(0L))

        else ->
            Array(maxAttempts - 1) {
                if (it < backoff.size) {
                    backoff[it]
                } else {
                    backoff[backoff.size - 1]
                }
            }
    }

    override fun delay(attempt: Int): Long {
        return if (attempt < 1 || attempt > delays.size) {
            -1
        } else {
            delays[attempt - 1]
        }
    }
}
