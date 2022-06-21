package net.corda.reconciliation

/**
 * Versioned records are returned by DB and Kafka [ReconcilerReader]s. They are used to determine
 * which records need updating on Kafka. A DB versioned record needs to be published on Kafka when:
 *
 * * It does not exist on Kafka.
 * * It's DB version is higher than it's Kafka version.
 * * It is deleted in the DB but exists on Kafka (i.e. it's value is not null on Kafka compacted topic).
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