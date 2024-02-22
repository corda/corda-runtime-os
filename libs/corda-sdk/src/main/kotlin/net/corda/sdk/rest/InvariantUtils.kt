package net.corda.sdk.rest

object InvariantUtils {

    const val MAX_ATTEMPTS = 10
    const val WAIT_INTERVAL = 2000L
    fun <T> checkInvariant(
        maxAttempts: Int = MAX_ATTEMPTS,
        waitInterval: Long = WAIT_INTERVAL,
        errorMessage: String,
        invariantCheck: () -> T?,
    ): T {
        var remainingAttempts = maxAttempts

        while (remainingAttempts > 0) {
            val result = invariantCheck()
            if (result != null) {
                return result
            }

            remainingAttempts--
            Thread.sleep(waitInterval)
        }

        throw InvariantException(errorMessage)
    }

    class InvariantException(message: String) : Exception(message)
}
