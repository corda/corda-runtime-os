package net.corda.messagebus.db.datamodel

import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

/**
 * Database entry representing the consumer committed offsets which have been
 * read and processed as part of a transaction.
 *
 * These will be used to ensure the read side of the messaging is kept up-to-date
 * outside of transactions.
 */
@Suppress("LongParameterList")
@Entity(name = "topic_consumer_offset")
@Table(name = "topic_consumer_offset")
@IdClass(CommittedOffsetEntryKey::class)
class CommittedPositionEntry(
    @Id
    val topic: String,

    @Id
    @Column(name = "consumer_group")
    val consumerGroup: String,

    @Id
    val partition: Int,

    @Id
    @Column(name = "record_offset")
    val recordPosition: Long,

    @ManyToOne
    @JoinColumn(name = "transaction_id")
    val transactionId: TransactionRecordEntry,

    @Column
    val timestamp: Instant = Instant.now(),
)

@Embeddable
data class CommittedOffsetEntryKey(
    val topic: String,
    @Column(name = "consumer_group")
    val consumerGroup: String,
    val partition: Int,
    @Column(name = "record_offset")
    val recordPosition: Long
): Serializable
