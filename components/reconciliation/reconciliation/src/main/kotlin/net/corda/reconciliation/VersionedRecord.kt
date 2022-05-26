package net.corda.reconciliation

/**
 * Input data for reconciliation, returned by (db and Kafka) [ReconcilerReader]s. Non nullable [value]s
 * means Kafka records with deleted values (null`ed values) are filtered out and cannot be returned by
 * [ReconcilerReader]s.
 */
interface VersionedRecord<K, V> {
    val version: Int
    val isDeleted: Boolean
    val key: K
    val value: V
}