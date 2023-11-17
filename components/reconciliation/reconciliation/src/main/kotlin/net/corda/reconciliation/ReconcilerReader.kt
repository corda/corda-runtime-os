package net.corda.reconciliation

import net.corda.lifecycle.LifecycleCoordinatorName
import java.util.stream.Stream

/**
 * Reads [VersionedRecord]s from the db and Kafka to be compared in the reconciliation process.
 * From the retrieved records a delta is going to be produced which is going to contain records
 * that are either missing on Kafka, or their versions is lower than their respective one on Kafka
 * or they have been deleted in the db but not on Kafka.
 *
 * Kafka deleted values (i.e. having null values) are filtered out from reading,
 * please see [VersionedRecord].
 */
interface ReconcilerReader<K : Any, V : Any> {
    /**
     * Gets records and their versions (input to the reconciliation process).
     *
     * Should an error occur in [getAllVersionedRecords] implementation, the owning [ReconcilerReader] lifecycle
     * should be notified about the error (by scheduling an error event to its lifecycle coordinator).
     * Then the exception should be re-thrown and not swallowed for the calling lifecycle service to
     * immediately know that an error occurred.
     */
    fun getAllVersionedRecords(): Stream<VersionedRecord<K, V>>

    val lifecycleCoordinatorName: LifecycleCoordinatorName
}