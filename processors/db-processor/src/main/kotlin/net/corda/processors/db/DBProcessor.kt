package net.corda.processors.db

import net.corda.libs.configuration.SmartConfig

/** The processor for a `DBWorker`. */
interface DBProcessor {
    fun start(config: SmartConfig)

    fun stop()
}