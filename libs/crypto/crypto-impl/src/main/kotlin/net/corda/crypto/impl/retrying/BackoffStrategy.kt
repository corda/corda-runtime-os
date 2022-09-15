package net.corda.crypto.impl.retrying

/**
 * Strategy to provide backoff delays when handling transient faults by retrying.
 */
fun interface BackoffStrategy {
    companion object {
        /**
         * Creates backoff strategy. If the number of attempts is less than max attempts then
         * the last values is repeated. If the backoff is empty then the time is set to zero.
         */
        @JvmStatic
        fun createBackoff(maxAttempts: Int, backoff: List<Long>): BackoffStrategy = when {
            maxAttempts <= 1 -> Default(emptyArray())
            backoff.isEmpty() -> createBackoff(maxAttempts, listOf(0L))
            else -> Default(
                Array(maxAttempts - 1) {
                    if (it < backoff.size) {
                        backoff[it]
                    } else {
                        backoff[backoff.size - 1]
                    }
                }
            )
        }
    }

    /**
     * Returns the next wait period in milliseconds for the given attempt.
     * The return value of -1 would mean that there is no further attempts to retry.
     *
     * @param attempt - the current attempt which failed, starts at 1.
     */
    fun getBackoff(attempt: Int): Long

    /**
     * Default implementation of the [BackoffStrategy]
     *
     * @property backoff defines the wait times between each attempt, the number of max attempts is backoff size plus 1.
     */
    class Default(
        private val backoff: Array<Long>
    ) : BackoffStrategy {
        override fun getBackoff(attempt: Int): Long =
            if (attempt < 1 || attempt > backoff.size) {
                -1
            } else {
                backoff[attempt - 1]
            }
    }
}
