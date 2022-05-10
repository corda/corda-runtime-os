package net.corda.reconciliation

import net.corda.lifecycle.LifecycleCoordinatorName

/**
 * Publishes record values on Kafka, that are out of sync on Kafka compared to their Db state,
 * identified from the reconciliation process.
 *
 * For now we are only publishing the value of the record because our DTOs contain both the key
 * and the value, therefore they also contain the key.
 */
interface ReconcilerWriter<V : Any> {
    /**
     * Publishes a record to Kafka.
     */
    fun put(record: V)

    /**
     * Removes a record from Kafka.
     */
    fun remove(record: V)

    val lifecycleCoordinatorName: LifecycleCoordinatorName
}