package net.corda.processors.db.internal.reconcile.db

/**
 * Any subclasses are assumed to be "reconcilers" that run periodically.
 */
interface ReconcilerWrapper {
    /**
     * Set the interval between reconciliations.
     *
     * Usage of this method will cause the subclass to start the initial reconciliation, or change an existing one.
     *
     * If this method has never been called no reconciliation will take place.
     */
    fun updateInterval(intervalMillis: Long)

    fun stop()
}
