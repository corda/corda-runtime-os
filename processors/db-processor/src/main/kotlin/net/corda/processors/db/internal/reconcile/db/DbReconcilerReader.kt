package net.corda.processors.db.internal.reconcile.db

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.reconciliation.ReconcilerReader
import java.lang.Exception

/**
 * Common interface for all [ReconcilerReader] implementations handling DB reads.
 */
interface DbReconcilerReader<K : Any, V : Any> : ReconcilerReader<K, V> {
    /**
     * Name used for logging and the lifecycle coordinator.
     */
    val name: String

    /**
     * Set of dependencies that an implementation on this class must follow lifecycle status of.
     */
    val dependencies: Set<LifecycleCoordinatorName>

    /**
     * Logic that should run when the reader's lifecycle status is UP.
     */
    fun onStatusUp()

    /**
     * Logic that should run when the reader's lifecycle status is DOWN.
     */
    fun onStatusDown()

    /**
     * Allows an exception handler to be attached and called in case exception occurs when retrieving versioned records.
     */
    fun registerExceptionHandler(exceptionHandler: (e: Exception) -> Unit): AutoCloseable
}
