package net.corda.crypto.impl

import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.slf4j.Logger
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

open class Executor(
    private val logger: Logger,
    private val retryCount: Int,
    private val waitBetween: Duration = Duration.ofMillis(100)
) {
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
                if(!e.isRecoverable()) {
                    logger.error("Failed to execute (opId=$opId)", e)
                    throw e
                }
                remaining--
                if(remaining <= 0) {
                    logger.error("Failed to execute (opId=$opId)", e)
                    throw e
                } else {
                    opId = UUID.randomUUID().toString()
                    logger.warn(
                        "Failed to execute, will retry after ${waitBetween.toMillis()} milliseconds" +
                                "(op=$opId,remaining=$remaining)",
                        e
                    )
                    Thread.sleep(waitBetween.toMillis())
                }
            }
        }
    }

    protected open fun <R> execute(block: () -> R): R = block()

    private fun Throwable.isRecoverable(): Boolean =
        when(this) {
            is TimeoutException -> true
            is IllegalArgumentException -> false
            is CryptoServiceLibraryException -> isRecoverable
            else -> true
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
