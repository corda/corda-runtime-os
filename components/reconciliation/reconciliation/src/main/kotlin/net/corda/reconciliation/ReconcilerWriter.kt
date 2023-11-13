package net.corda.reconciliation

import net.corda.lifecycle.LifecycleCoordinatorName

/**
 * Publishes records to Kafka, that are out of sync on Kafka compared to their DB state,
 * identified from the reconciliation process.
 */
interface ReconcilerWriter<K : Any, V : Any> {
    /**
     * Publishes a record to Kafka.
     */
    fun put(recordKey: K, recordValue: V)

    /**
     * Removes a record from Kafka.
     */
    fun remove(recordKey: K)

    val lifecycleCoordinatorName: LifecycleCoordinatorName

    /**
     * Some writers allow the checking of records to see if the value content has changed
     * between the db and Kafka before pushing out a new value.
     */
    fun valuesMisalignedAfterDefaults(
        recordKey: K,
        dbRecordValue: V,
        kafkaRecordValue: V
    ): Boolean = false
}