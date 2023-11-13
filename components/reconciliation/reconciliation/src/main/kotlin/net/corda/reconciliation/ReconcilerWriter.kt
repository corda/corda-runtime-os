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
     * Compare DB record value to its Kafka respective one to see if after applying defaults
     * to the DB record, the DB record is different to the Kafka one.
     */
    fun valuesMisalignedAfterDefaults(
        recordKey: K,
        dbRecordValue: V,
        kafkaRecordValue: V
    ): Boolean = false
}