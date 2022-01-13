package net.corda.processors.db

import net.corda.libs.configuration.SmartConfig

/** The processor for a `DBWorker`. */
interface DBProcessor {
    /**
     * Starts performing the work of the DB worker.
     *
     * @throws DBProcessorException If the cluster database cannot be connected to.
     */
    fun start(config: SmartConfig)

    fun stop()
}