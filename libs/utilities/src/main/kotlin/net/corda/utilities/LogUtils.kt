package net.corda.utilities

import org.slf4j.Logger
import java.io.OutputStream
import java.io.PrintStream
import java.time.Duration
import java.util.Collections

inline fun <T> logElapsedTime(label: String, logger: Logger, body: () -> T): T {
    // Use nanoTime as it's monotonic.
    val now = System.nanoTime()
    var failed = false
    try {
        return body()
    } catch (th: Throwable) {
        failed = true
        throw th
    } finally {
        val elapsed = Duration.ofNanos(System.nanoTime() - now).toMillis()
        val msg = (if (failed) "Failed " else "") + "$label took $elapsed msec"
        logger.info(msg)
    }
}

private const val MAX_SIZE = 100
private val warnings = Collections.newSetFromMap(createSimpleCache<String, Boolean>(MAX_SIZE)).toSynchronised()

/**
 * Utility to help log a warning message only once.
 * It implements an ad hoc Fifo cache because there's none available in the standard libraries.
 */
fun Logger.warnOnce(warning: String) {
    if (warnings.add(warning)) {
        this.warn(warning)
    }
}

/**
 * Run a code block temporary suppressing any StdErr output that might be produced
 */
fun <T : Any?> executeWithStdErrSuppressed(block: () -> T) : T {
    val initial = System.err
    return try {
        System.setErr(PrintStream(OutputStream.nullOutputStream()))
        block()
    } finally {
        System.setErr(initial)
    }
}