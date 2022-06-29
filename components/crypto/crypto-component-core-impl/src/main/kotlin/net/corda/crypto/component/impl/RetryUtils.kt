package net.corda.crypto.component.impl

import org.slf4j.Logger

inline fun <R> retry(numRetries: Int, logger: Logger, block: () -> R): R {
    var firstException: Exception? = null
    for (i in 0..numRetries) {
        try {
            return block()
        } catch (e: Exception) {
            logger.warn("Exception occurred in retry block (invocation: ${i+1}, retries left: ${numRetries-i}): $e")
            if (firstException == null)
                firstException = e
        }
    }
    throw firstException!!
}
