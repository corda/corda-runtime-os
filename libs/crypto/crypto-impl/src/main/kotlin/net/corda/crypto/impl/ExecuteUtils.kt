package net.corda.crypto.impl

import org.slf4j.Logger
import java.time.Duration
import java.util.UUID

fun <R> executeWithRetry(
    logger: Logger,
    retryCount: Int,
    block: () -> R
): R = executeWithRetry(logger, retryCount, Duration.ofMillis(100), block)

fun <R> executeWithRetry(
    logger: Logger,
    retryCount: Int,
    waitBetween: Duration,
    block: () -> R
): R {
    var remaining = retryCount
    var opId = ""
    while (true) {
        try {
            if(remaining < retryCount) {
                logger.info("Retrying operation (op={}},remaining={})", opId, remaining)
            }
            val result = block()
            if(remaining < retryCount) {
                logger.info("Retrying was successful (op={}},remaining={})", opId, remaining)
            }
            return result
        } catch (e: Throwable) {
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