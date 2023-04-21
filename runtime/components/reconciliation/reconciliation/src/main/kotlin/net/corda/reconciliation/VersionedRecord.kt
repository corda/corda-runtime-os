package net.corda.reconciliation

/**
 * Versioned records are returned by DB and Kafka [ReconcilerReader]s. They are used to determine
 * which records need updating in Kafka. A DB versioned record needs to be updated in Kafka when:
 *
 * - It does not exist in Kafka.
 * - Its DB version is higher than its Kafka version.
 * - It is deleted in the DB but exists in Kafka (i.e. its value is not null in Kafka compacted topic).
 *
 * Note1: DB records that are to be read as part of reconciliation need to be soft DB deleted and
 * [VersionedRecord.isDeleted] needs to be populated accordingly.
 *
 * Note2: Non nullable [value]s means Kafka records with deleted values (null`ed values) are
 * filtered out and are not returned by Kafka [ReconcilerReader]s.
 */
interface VersionedRecord<K, V> {
    val version: Int
    val isDeleted: Boolean
    val key: K
    val value: V
}