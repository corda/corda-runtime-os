package net.corda.processors.flow

import net.corda.libs.configuration.SmartConfig

/** The processor for a `FlowWorker`. */
interface FlowProcessor {
    fun start(instanceId: Int, topicPrefix: String, config: SmartConfig)

    fun stop()
}