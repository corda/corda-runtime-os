package net.corda.cli.plugins.network.utils

object InvariantUtils {
    fun <T> checkInvariant(
        maxAttempts: Int,
        waitInterval: Long,
        errorMessage: String,
        invariantCheck: () -> T?
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

        throw RuntimeException(errorMessage)
    }
}