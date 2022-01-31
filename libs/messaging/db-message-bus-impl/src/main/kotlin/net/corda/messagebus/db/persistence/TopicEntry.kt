package net.corda.messagebus.db.persistence

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity(name = "topic")
@Table(name = "topic")
class TopicEntry(
    @Id
    val topic: String,

    @Column
    val numPartitions: Int
)
