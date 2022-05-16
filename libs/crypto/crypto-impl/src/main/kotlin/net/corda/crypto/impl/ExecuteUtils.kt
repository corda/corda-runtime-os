package net.corda.crypto.impl

import org.slf4j.Logger
import java.time.Duration

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
    while (true) {
        try {
            if(remaining < retryCount) {
                logger.info("Retrying operation (remaining=$remaining)")
            }
            val result = block()
            if(remaining < retryCount) {
                logger.info("Retrying was successful (remaining=$remaining)")
            }
            return result
        } catch (e: Throwable) {
            remaining--
            if(remaining <= 0) {
                logger.error("Failed to execute", e)
                throw e
            } else {
                logger.warn(
                    "Failed to execute, will retry after ${waitBetween.toMillis()} milliseconds (remaining=$remaining)",
                    e
                )
                Thread.sleep(waitBetween.toMillis())
            }
        }
    }
}