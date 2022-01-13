package net.corda.processors.flow

import net.corda.libs.configuration.SmartConfig

/** The processor for a `FlowWorker`. */
interface FlowProcessor {
    fun start(config: SmartConfig)

    fun stop()
}