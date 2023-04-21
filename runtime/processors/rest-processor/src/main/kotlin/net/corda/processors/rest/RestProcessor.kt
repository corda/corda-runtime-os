package net.corda.processors.rest

import net.corda.libs.configuration.SmartConfig

/** The processor for a `RestWorker`. */
interface RestProcessor {
    fun start(bootConfig: SmartConfig)

    fun stop()
}