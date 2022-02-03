package net.corda.messagebus.db.datamodel

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity(name = "topic")
@Table(name = "topic")
class TopicEntry(
    @Id
    val topic: String,

    @Column(name = "num_partitions")
    val numPartitions: Int
)
