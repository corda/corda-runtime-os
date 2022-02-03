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
    @Column(name = "offset")
    val offset: Long,
    @Column
    val key: ByteArray,
    @Column
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
    val offset: Long,
): Serializable
