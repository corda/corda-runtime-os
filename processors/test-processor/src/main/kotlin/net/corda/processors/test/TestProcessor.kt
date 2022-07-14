package net.corda.processors.test

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

/** The processor for a `FlowWorker`. */
interface TestProcessor {
    fun start(bootConfig: SmartConfig)

    fun stop()
}