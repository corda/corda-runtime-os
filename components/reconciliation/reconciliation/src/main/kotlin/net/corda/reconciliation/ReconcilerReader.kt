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
     * Gets records and their versions (input to the reconciliation process). Implementations of
     * this method should not allow exceptions escape it, instead they should be notifying the
     * [ReconcilerReader] lifecycle service about the error occurred by scheduling an error event.
     *
     * For the calling service to know immediately that an error occurred a null value should be returned.
     * Subsequently, the calling service, that should be following [ReconcilerReader], will as well get
     * notified of the [ReconcilerReader]'s changed status by a RegistrationStatusChangeEvent.
     */
    fun getAllVersionedRecords(): Stream<VersionedRecord<K, V>>?

    val lifecycleCoordinatorName: LifecycleCoordinatorName
}