package net.corda.reconciliation

import net.corda.lifecycle.Lifecycle

/**
 * Marker interface representing a reconciler that will handle db to Kafka compacted topic reconciliation.
 *
 * The db data and Kafka compacted topic data to be reconciled will be declared through [ReconcilerReader]s/
 * [ReconcilerWriter].
 *
 * On [start] it will start running reconciliations periodically, using its underlying [ReconcilerReader]s/
 * [ReconcilerWriter]. On error, if the error is transient it will go [LifecycleStatus.DOWN] and try to
 * resume later, or will go [LifecycleStatus.ERROR] otherwise. On [stop] it will stop reconciliations.
 */
interface Reconciler : Lifecycle