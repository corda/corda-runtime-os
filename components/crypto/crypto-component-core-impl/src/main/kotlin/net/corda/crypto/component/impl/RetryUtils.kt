package net.corda.crypto.component.impl

import org.slf4j.Logger
import kotlin.math.pow
import kotlin.math.roundToLong

@Suppress("NestedBlockDepth")
inline fun <R> retry(numRetries: Int, logger: Logger, block: () -> R): R {
    var firstException: Exception? = null
    var i = 0
    while (true) {
        if (i > 0) Thread.sleep((1000 * 1.0.pow(i + 1)).roundToLong())
        try {
            return block()
        } catch (e: Exception) {
            logger.warn("Exception occurred in retry block (invocation: ${i + 1}, retries left: ${numRetries - i}): $e")
            if (firstException == null)
                firstException = e
        }
        i += 1
        if (i >= numRetries) throw firstException!!
    }
}
