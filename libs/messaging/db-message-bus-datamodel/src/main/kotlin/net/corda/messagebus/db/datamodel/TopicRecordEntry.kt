package net.corda.messagebus.db.datamodel

import java.io.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
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
    var topic: String,

    @Id
    @Column
    var partition: Int,

    @Id
    @Column(name = "record_offset")
    var recordOffset: Long,

    /**
     * NOTE: All keys must end up on the same partition
     */
    @Column(name = "record_key")
    var key: ByteArray,

    @Column(name = "record_value")
    var value: ByteArray?,

    @Column(name = "record_headers")
    var headers: String?,

    @ManyToOne
    @JoinColumn(name = "transaction_id")
    var transactionId: TransactionRecordEntry,

    @Column
    var timestamp: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS),
)

@Embeddable
data class TopicRecordEntryKey(
    var topic: String,
    var partition: Int,
    var recordOffset: Long,
) : Serializable
