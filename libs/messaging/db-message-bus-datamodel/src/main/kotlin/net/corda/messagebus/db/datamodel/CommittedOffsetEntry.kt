package net.corda.messagebus.db.datamodel

import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Table

@Suppress("LongParameterList")
@Entity(name = "topic_consumer_offset")
@Table(name = "topic_consumer_offset")
@IdClass(CommittedOffsetEntryKey::class)
class CommittedOffsetEntry (

    @Id
    val topic: String,

    @Id
    @Column(name = "consumer_group")
    val consumerGroup: String,

    @Id
    val partition: Int,

    @Id
    @Column(name = "record_offset")
    val recordOffset: Long,

    @Column
    val timestamp: Instant = Instant.now(),

    @Column
    val committed: Boolean = false
)

@Embeddable
data class CommittedOffsetEntryKey(
    val topic: String,
    @Column(name = "consumer_group")
    val consumerGroup: String,
    val partition: Int,
    @Column(name = "record_offset")
    val recordOffset: Long
): Serializable
