package net.corda.logging.mdc

import org.slf4j.MDC

/**
 * Push the map of [mdcData] into the logging MDC
 */
fun Any.pushLoggingMDC(mdcData: Map<String, String>) {
    MDC.setContextMap(mdcData)
}

/**
 * Clear the logging MDC of the set of keys in [mdcDataKeys]
 */
fun Any.clearLoggingMDC(mdcDataKeys: Set<String>) {
    MDC.getMDCAdapter().apply {
        mdcDataKeys.forEach {
            remove(it)
        }
    }
}

/**
 * Clear the logging MDC of the data stored in [mdcData]
 */
fun Any.clearLoggingMDC(mdcData: Map<String, String>) {
    clearLoggingMDC(mdcData.keys)
}

/**
 * Clear the Log4j logging MDC of all data stored there.
 */
fun Any.clearLoggingMDC() {
    MDC.clear()
}