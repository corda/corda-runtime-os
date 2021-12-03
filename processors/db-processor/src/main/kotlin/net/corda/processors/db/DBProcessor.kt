package net.corda.processors.db

import net.corda.libs.configuration.SmartConfig

/** The processor for a `DBWorker`. */
interface DBProcessor {
    fun start(instanceId: Int, config: SmartConfig)

    fun stop()
}