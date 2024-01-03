package net.corda.crypto.impl.retrying

import net.corda.crypto.core.CryptoRetryException
import net.corda.crypto.core.isRecoverable
import net.corda.utilities.retry.BackoffStrategy
import net.corda.utilities.retry.PreSet
import net.corda.utilities.retry.tryWithBackoff
import org.slf4j.Logger

/**
 * Basic block executor with the retry behaviour.
 */
open class CryptoRetryingExecutor(
    private val logger: Logger,
    private val maxAttempts: Long,
    waitBetweenMills: List<Long>
) {
    private val backoffStrategy: BackoffStrategy

    init {
        val delays: List<Long> = when {
            maxAttempts <= 1 -> emptyList()
            waitBetweenMills.isEmpty() -> List(maxAttempts.toInt()) { 0L }
            else ->
                List(maxAttempts.toInt() - 1) {
                    if (it < waitBetweenMills.size) {
                        waitBetweenMills[it]
                    } else {
                        waitBetweenMills[waitBetweenMills.size - 1]
                    }
                }
        }

        backoffStrategy = PreSet(delays)
    }

    /**
     * Executes the block, if the exception is recoverable it'll retry using supplied strategy.
     *
     * @throws CryptoRetryException if the original exception was recoverable (the original is set as the cause) and
     * all attempts to retry haven't succeeded. If the original exception is not recoverable then it'll be rethrown.
     */
    @Suppress("NestedBlockDepth")
    fun <R> executeWithRetry(block: () -> R): R {
        return tryWithBackoff(
            logger = logger,
            maxRetries = maxAttempts,
            maxTimeMillis = Long.MAX_VALUE,
            backoffStrategy = backoffStrategy,
            recoverable = { _, _, throwable -> throwable.isRecoverable() },
            exceptionProvider = { m, t -> CryptoRetryException(m, t) }
        ) {
            block()
        }
    }
}
