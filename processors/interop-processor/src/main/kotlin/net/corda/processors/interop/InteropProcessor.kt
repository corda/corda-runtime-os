package net.corda.processors.interop

import net.corda.libs.configuration.SmartConfig

/** The processor for a `FlowWorker`. */
interface InteropProcessor {
    fun start(bootConfig: SmartConfig)

    fun stop()
}