package net.corda.crypto.impl.retrying

import net.corda.crypto.core.CryptoRetryException
import net.corda.crypto.core.isRecoverable
import net.corda.utilities.debug
import org.slf4j.Logger
import java.util.UUID

/**
 * Basic block executor with the retry behaviour.
 */
open class CryptoRetryingExecutor(
    private val logger: Logger,
    private val strategy: BackoffStrategy
) {
    companion object {
        const val CRYPTO_MAX_ATTEMPT_GUARD: Int = 10
    }

    init {
        logger.debug  { "Using ${strategy::class.java.name} retry strategy." }
    }

    /**
     * Executes the block, if the exception is recoverable it'll retry using supplied strategy.
     *
     * @throws CryptoRetryException if the original exception was recoverable (the original is set as the cause) and
     * all attempts to retry haven't succeeded. If the original exception is not recoverable then it'll be rethrown.
     */
    @Suppress("NestedBlockDepth")
    fun <R> executeWithRetry(block: () -> R): R {
        var attempt = 1
        var op = ""
        while (true) {
            try {
                if (attempt > 1) {
                    logger.info("Retrying operation (op={},attempt={})", op, attempt)
                }
                val result = execute(block)
                if (attempt > 1) {
                    logger.info("Retrying was successful (op={},attempt={})", op, attempt)
                }
                return result
            } catch (e: Throwable) {
                if (!e.isRecoverable()) {
                    // the exception is not recoverable, no point in retrying
                    logCompleteFailure(attempt, op)
                    // throws the original exception
                    throw e
                }
                val backoff = strategy.getBackoff(attempt)
                if (backoff < 0 || attempt > CRYPTO_MAX_ATTEMPT_GUARD) {
                    // the strategy is exhausted, giving up
                    logCompleteFailure(attempt, op)
                    // throws the CryptoRetryException only because the original exception was recoverable
                    throw CryptoRetryException("Failed to execute on attempt=$attempt", e)
                } else {
                    attempt++
                    if(op.isEmpty()) {
                        op = UUID.randomUUID().toString()
                    }
                    logger.warn(
                        "Failed to execute, will retry after $backoff milliseconds (op=$op,attempt=$attempt)",
                        e
                    )
                    // sleep for a little while and then retry
                    Thread.sleep(backoff)
                }
            }
        }
    }

    private fun logCompleteFailure(attempt: Int, opId: String) {
        logger.warn("Failed to execute (opId={},attempt={})", opId, attempt)
    }

    protected open fun <R> execute(block: () -> R): R = block()
}
