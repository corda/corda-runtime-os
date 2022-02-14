package net.corda.messagebus.db.datamodel

import java.io.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Table

/**
 * The entity represents the actual record entry.  In code, it will be partitioned by
 * [topic] and [partition].  The primary fields are [key]/[value].  [transactionId]
 * will map to the values in `transaction_record` (see [TransactionRecordEntry]) to
 * determine if the record is part of a [PENDING], [COMMITTED], or [ABORTED] transaction.
 */
@Suppress("LongParameterList")
@Entity(name = "topic_record")
@Table(name = "topic_record")
@IdClass(TopicRecordEntryKey::class)
class TopicRecordEntry(
    @Id
    @Column
    val topic: String,
    @Id
    @Column
    val partition: Int,
    @Id
    @Column(name = "record_offset")
    val recordOffset: Long,
    /**
     * NOTE: All keys must end up on the same partition
     */
    @Column(name = "record_key")
    val key: ByteArray,
    @Column(name = "record_value")
    val value: ByteArray?,
    @Column(name = "transaction_id")
    val transactionId: String,
    @Column
    val timestamp: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS),
)

@Embeddable
data class TopicRecordEntryKey(
    val topic: String,
    val partition: Int,
    val recordOffset: Long,
): Serializable
