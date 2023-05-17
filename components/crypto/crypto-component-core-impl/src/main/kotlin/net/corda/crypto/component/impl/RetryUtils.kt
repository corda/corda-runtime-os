package net.corda.crypto.component.impl

import org.slf4j.Logger
import kotlin.math.pow
import kotlin.math.roundToLong

inline fun <R> retry(numRetries: Int, logger: Logger, block: () -> R): R {
    if (numRetries == 0) {
        return block()
    } else {
        var firstException: Exception? = null
        for (i in 0..numRetries) {
            try {
                return block()
            } catch (e: Exception) {
                logger.warn("Exception occurred in retry block (invocation: ${i + 1}, retries left: ${numRetries - i}): $e")
                if (firstException == null)
                    firstException = e
                Thread.sleep((1000 * 1.0.pow(i + 1)).roundToLong())
            }
        }
        throw firstException!!
    }
}
