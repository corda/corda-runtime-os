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

    // The below API currently only applies to Config reconciliation in an attempt to address CORE-18268.
    // It currently seems quite specific to Config reconciliation and therefore, ideally, should be removed.
    // In which case Config reconciliation should be aligned with the rest of the reconciliations, i.e.
    // the defaulting process should be done prior to reading the db records (in that case forced reconciliation concept
    // will also, no longer be needed).
    fun recordValueMisalignedAfterDefaults(
        recordKey: K,
        dbRecordValue: V,
        kafkaRecordValue: V
    ): Boolean = false
}