package net.corda.messagebus.db.persistence

import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Table

@Entity(name = "topic_consumer_offset")
@Table(name = "topic_consumer_offset")
@IdClass(CommittedOffsetEntryKey::class)
class CommittedOffsetEntry (

    @Id
    val topic: String,

    @Id
    val consumer_group: String,

    @Id
    val partition: Int,

    @Id
    val offset: Long,

    @Column
    val timestamp: Instant = Instant.now(),

    @Column
    val committed: Boolean = false
)

@Embeddable
data class CommittedOffsetEntryKey(
    val topic: String,
    val consumer_group: String,
    val partition: Int,
    val offset: Long
): Serializable
