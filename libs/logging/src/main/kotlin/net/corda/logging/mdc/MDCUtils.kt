package net.corda.logging.mdc

import org.slf4j.MDC

fun Any.pushMDCLogging(mdcData: Map<String, String>) {
    MDC.setContextMap(mdcData)
}

fun Any.clearMDCLogging(mdcDataKeys: Set<String>) {
    MDC.getMDCAdapter().apply {
        mdcDataKeys.forEach {
            remove(it)
        }
    }
}

fun Any.clearMDCLogging(mdcData: Map<String, String>) {
    clearMDCLogging(mdcData.keys)
}

fun Any.clearMDCLogging() {
    MDC.clear()
}