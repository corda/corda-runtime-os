package net.corda.processors.test

import net.corda.libs.configuration.SmartConfig

/** The processor for a `FlowWorker`. */
interface TestProcessor {
    fun start(bootConfig: SmartConfig)

    fun stop()
}
