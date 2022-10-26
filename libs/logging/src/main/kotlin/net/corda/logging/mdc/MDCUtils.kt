package net.corda.logging.mdc

import org.slf4j.MDC

/**
 * Push the map of [mdcProperties] into the logging MDC, run the code provided in [block] and then remove the [mdcProperties]
 * @param mdcProperties properties to push into mdc and then remove at the end
 * @param block the function to execute whose result is returned
 * @return the result of the [block] function
 */
fun <R> withMDCAndReturn(mdcProperties: Map<String, String>, block: () -> R) : R {
    try {
        setMDC(mdcProperties)
        return block()
    } finally {
        clearMDC(mdcProperties)
    }
}

/**
 * Push the map of [mdcProperties] into the logging MDC, run the code provided in [block] and then remove the [mdcProperties]
 */
fun withMDC(mdcProperties: Map<String, String>, block: () -> Unit) {
    try {
        setMDC(mdcProperties)
        block()
    } finally {
        clearMDC(mdcProperties)
    }
}

/**
 * Push the map of [mdcData] into the logging MDC
 */
fun setMDC(mdcData: Map<String, String>) {
    MDC.setContextMap(mdcData)
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
    MDC.clear()
}