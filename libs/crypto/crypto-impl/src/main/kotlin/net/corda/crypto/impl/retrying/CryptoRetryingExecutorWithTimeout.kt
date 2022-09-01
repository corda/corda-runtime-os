package net.corda.crypto.impl.retrying

import net.corda.base.concurrent.getOrThrow
import net.corda.utilities.concurrent.SecManagerForkJoinPool
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
    override fun <R> execute(block: () -> R): R =
        CompletableFuture.supplyAsync(block, SecManagerForkJoinPool.pool).getOrThrow(attemptTimeout)
}
