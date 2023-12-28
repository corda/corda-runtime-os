package net.corda.crypto.impl.retrying

import net.corda.crypto.core.CryptoRetryException
import net.corda.crypto.core.isRecoverable
import net.corda.utilities.retry.tryWithBackoff
import org.slf4j.Logger

/**
 * Basic block executor with the retry behaviour.
 */
open class CryptoRetryingExecutor(
    private val logger: Logger,
    private val strategy: CryptoBackoffStrategy
) {
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
            maxRetries = strategy.maxRetries,
            maxTimeMillis = Long.MAX_VALUE,
            backoffStrategy = strategy,
            recoverable = Throwable::isRecoverable,
            exceptionProvider = { m, t -> CryptoRetryException(m, t) }
        ) {
            block()
        }
    }
}
