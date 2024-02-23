package net.corda.processors.persistence

import net.corda.libs.configuration.SmartConfig

/** The processor for a `PersistenceWorker`. All this processor will actually do is set up the
 * database connection manager once Corda configuration is available.
 *
 * The entry point to the persistence processor is actually []LedgerPersistenceRequestProcessor]
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