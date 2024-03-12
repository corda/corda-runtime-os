package net.corda.utilities

import net.corda.v5.application.flows.FlowContextProperties.CORDA_RESERVED_PREFIX
import org.slf4j.Logger
import org.slf4j.MDC
import java.io.OutputStream
import java.io.PrintStream
import java.time.Duration
import java.util.Collections

/**
 * Common MDC properties used across corda.
 * The name of the MDC properties should be prefixed with `corda.` so we can tell from the logs which MDC values
 * were set explicitly by corda from those that were set by a third-party library.
 */
const val MDC_CLIENT_ID = "corda.client.id"
const val MDC_FLOW_ID = "corda.flow.id"
const val MDC_VNODE_ID = "corda.vnode.id"
const val MDC_SESSION_EVENT_ID = "corda.session.event.id"
const val MDC_EXTERNAL_EVENT_ID = "corda.external.event.id"
const val MDC_USER = "corda.http.user"
const val MDC_METHOD = "corda.http.method"
const val MDC_PATH = "corda.http.path"
const val MDC_LOGGED_PREFIX = "corda.logged"

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

/**
 * Push the map of [mdcProperties] into the logging MDC, run the code provided in [block] and then remove the [mdcProperties]
 * @param mdcProperties properties to push into mdc and then remove at the end
 * @param block the function to execute whose result is returned
 * @return the result of the [block] function
 */
fun <R> withMDC(mdcProperties: Map<String, String>, block: () -> R) : R {
    try {
        setMDC(mdcProperties)
        return block()
    } finally {
        clearMDC(mdcProperties)
    }
}

fun translateFlowContextToMDC(
    flowContextProperties: Map<String, String>
): Map<String, String> = flowContextProperties.filter {
    it.key.startsWith(MDC_LOGGED_PREFIX)
}.mapKeys {
    it.key.replace("$MDC_LOGGED_PREFIX.", CORDA_RESERVED_PREFIX)
}

/**
 * Push the map of [mdcData] into the logging MDC
 */
fun setMDC(mdcData: Map<String, String>) {
    MDC.getMDCAdapter().apply {
        mdcData.forEach {
            put(it.key, it.value)
        }
    }
}

/**
 * Clear the logging MDC of the set of keys in [mdcDataKeys]
 */
fun clearMDC(mdcDataKeys: Set<String>) {
    MDC.getMDCAdapter().apply {
        mdcDataKeys.forEach {
            remove(it)
        }
    }
}

/**
 * Clear the logging MDC of the data stored in [mdcData]
 */
fun clearMDC(mdcData: Map<String, String>) {
    clearMDC(mdcData.keys)
}

/**
 * Clear the Log4j logging MDC of all data stored there.
 */
fun clearMDC() {
    // Clear only the MDC properties that are explicitly set by Corda    // Some libraries like Brave also manage the MDC properties and those
    // properties shouldn't be cleared out.
    MDC.getMDCAdapter().apply {
        remove(MDC_CLIENT_ID)
        remove(MDC_FLOW_ID)
        remove(MDC_VNODE_ID)
        remove(MDC_SESSION_EVENT_ID)
        remove(MDC_EXTERNAL_EVENT_ID)
        remove(MDC_USER)
        remove(MDC_METHOD)
        remove(MDC_PATH)
    }
}