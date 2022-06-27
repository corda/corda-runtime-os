package net.corda.crypto.impl.retrying

import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.crypto.failures.CryptoRetryStrategy
import org.slf4j.Logger
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * Block executor with the retry behaviour and timeout limit for each attempt.
 */
class CryptoRetryingExecutorWithTimeout(
    logger: Logger,
    strategy: CryptoRetryStrategy,
    private val attemptTimeout: Duration?,
) : CryptoRetryingExecutor(logger, strategy) {
    override fun <R> execute(block: () -> R): R = CompletableFuture.supplyAsync(block).getOrThrow(attemptTimeout)
}
