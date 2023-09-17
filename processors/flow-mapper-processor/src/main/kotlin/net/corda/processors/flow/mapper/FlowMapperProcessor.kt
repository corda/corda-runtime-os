package net.corda.processors.flow.mapper

import net.corda.libs.configuration.SmartConfig

/** The processor for a `FlowMapperWorker`. */
interface FlowMapperProcessor {
    fun start(bootConfig: SmartConfig)

    fun stop()
}