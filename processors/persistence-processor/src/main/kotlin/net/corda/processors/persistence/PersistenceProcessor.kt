package net.corda.processors.persistence

import net.corda.libs.configuration.SmartConfig

/** The processor for a `PersistenceWorker`. */
interface PersistenceProcessor {
    /**
     * Starts performing the work of the Persistence worker.
     */
    fun start(bootConfig: SmartConfig)

    fun stop()
}