package net.corda.libs.kafka.topic.utils

import java.util.*


/**
 * Interface for creating Kafka topics
 * Instances of this class can be created via [TopicUtilsFactory]
 */
interface TopicUtils {

    /**
     * Create new topic based on:
     * [topicName]
     * [partitions]
     * [replication]
     */
    fun createTopic(topicName: String, partitions: Int, replication: Short)

    /**
     * Create new compacted topic based on:
     * [topicName]
     * [partitions]
     * [replication]
     */
    fun createCompactedTopic(topicName: String, partitions: Int, replication: Short)
}