package net.corda.processors.db.internal.reconcile.db

import net.corda.lifecycle.Lifecycle
import net.corda.reconciliation.ReconcilerReader

/**
 * Common interface for all [ReconcilerReader] implementations handling DB reads.
 */
interface DbReconcilerReader<K : Any, V : Any>: ReconcilerReader<K, V>, Lifecycle
