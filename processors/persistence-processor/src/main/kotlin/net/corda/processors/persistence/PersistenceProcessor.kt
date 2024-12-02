package net.corda.processors.persistence

import net.corda.libs.configuration.SmartConfig

/** The processor for a `PersistenceWorker`.
 *
 * This interface is an OSGi dependency of the [PersistenceWorker] and [CombinedWorker], which
 * call start and stop as the worker process that contains an PersistenceProcessor object starts
 * and stops.
 **/

interface PersistenceProcessor {
    /**
     * Starts performing the work of the Persistence worker.
     */
    fun start(bootConfig: SmartConfig)

    fun stop()
}