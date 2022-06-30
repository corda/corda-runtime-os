package net.corda.crypto.impl.retrying

import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.exceptions.BackoffStrategy
import org.slf4j.Logger
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * Block executor with the retry behaviour and timeout limit for each attempt.
 */
class CryptoRetryingExecutorWithTimeout(
    logger: Logger,
    strategy: BackoffStrategy,
    private val attemptTimeout: Duration?,
) : CryptoRetryingExecutor(logger, strategy) {
    override fun <R> execute(block: () -> R): R = CompletableFuture.supplyAsync(block).getOrThrow(attemptTimeout)
}
