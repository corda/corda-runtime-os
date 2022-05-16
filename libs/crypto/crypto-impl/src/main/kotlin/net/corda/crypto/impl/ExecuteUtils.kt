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
            return block()
        } catch (e: Throwable) {
            remaining--
            if(remaining <= 0) {
                logger.error("Failed to execute", e)
                throw e
            } else {
                logger.error(
                    "Failed to execute, will retry after ${waitBetween.toMillis()} milliseconds (remaining=$remaining)",
                    e
                )
                Thread.sleep(waitBetween.toMillis())
            }
        }
    }
}