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
) {
    override fun toString(): String {
        return "TopicEntry(topic='$topic', numPartitions=$numPartitions)"
    }
}
/**
 * This entity represents the topic metadata.  This entity will be
 * used to insert topics when initialising the db.
 */
@Entity(name = "topic")
@Table(name = "messagebus.topic")
class TopicEntryForInsert(
    @Id
    @Column(name = "topic")
    val topic: String,

    @Column(name = "num_partitions")
    val numPartitions: Int
) {
    override fun toString(): String {
        return "TopicEntry(topic='$topic', numPartitions=$numPartitions)"
    }
}
