package net.corda.messagebus.db.datamodel

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * This entity represents the topic metadata.  This information will primarily be
 * used when restarting to rebuild the internal state for the producers/consumers,
 * (ie. creating multiple [CordaTopicPartition]s for consumers to subscribe/assign to.
 */
@Entity(name = "topic")
@Table(name = "topic")
class TopicEntry(
    @Id
    val topic: String,

    @Column(name = "num_partitions")
    val numPartitions: Int
)
