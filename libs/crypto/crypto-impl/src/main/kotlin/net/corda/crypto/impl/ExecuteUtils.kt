package net.corda.crypto.impl

import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.slf4j.Logger
import java.lang.Integer.min
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

open class Executor(
    private val logger: Logger,
    rawRetryCount: Int,
    private val waitBetween: Duration = Duration.ofMillis(100)
) {
    companion object {
        private const val MAX_RETRY_GUARD: Int = 10

        // don't want to depend here on the JPA directly hence the class names
        val RETRYABLE_EXCEPTIONS = setOf(
            "javax.persistence.LockTimeoutException",
            "javax.persistence.QueryTimeoutException",
            "javax.persistence.OptimisticLockException",
            "javax.persistence.PessimisticLockException"
        )
    }

    private val retryCount = min(rawRetryCount, MAX_RETRY_GUARD)

    fun <R> executeWithRetry(block: () -> R): R {
        var remaining = retryCount
        var opId = ""
        while (true) {
            try {
                if (remaining < retryCount) {
                    logger.info("Retrying operation (opId={},remaining={})", opId, remaining)
                }
                val result = execute(block)
                if (remaining < retryCount) {
                    logger.info("Retrying was successful (opId={},remaining={})", opId, remaining)
                }
                return result
            } catch (e: Throwable) {
                if (!e.isRecoverable()) {
                    // the exception is not recoverable, not point in retrying
                    logger.error("Failed to execute (opId=$opId)", e)
                    throw e
                }
                remaining--
                if (remaining <= 0) {
                    // the number of retries is exhausted, giving up
                    logger.error("Failed to execute (opId=$opId)", e)
                    throw e
                } else {
                    opId = UUID.randomUUID().toString()
                    logger.warn(
                        "Failed to execute, will retry after ${waitBetween.toMillis()} milliseconds" +
                                "(op=$opId,remaining=$remaining)",
                        e
                    )
                    // sleep for a little while and then retry
                    Thread.sleep(waitBetween.toMillis())
                }
            }
        }
    }

    protected open fun <R> execute(block: () -> R): R = block()

    private fun Throwable.isRecoverable(): Boolean =
        when (this) {
            is TimeoutException -> true
            is CryptoServiceLibraryException -> isRecoverable
            else -> when {
                RETRYABLE_EXCEPTIONS.contains(this::class.java.name) -> true
                cause != null -> cause!!.isRecoverable()
                else -> false
            }
        }
}

open class ExecutorWithTimeout(
    logger: Logger,
    retryCount: Int,
    private val retryTimeout: Duration?,
    waitBetween: Duration = Duration.ofMillis(100)
) : Executor(logger, retryCount, waitBetween) {
    override fun <R> execute(block: () -> R): R = CompletableFuture.supplyAsync(block).getOrThrow(retryTimeout)
}
