package net.corda.libs.kafka.topic.utils

import java.util.*


/**
 * Kafka utility for topic administration
 */
interface TopicUtils {

    /**
     * Create new topic based on:
     * [topicName]
     * [partitions]
     * [replication]
     * [kafkaProps]
     */
    fun createTopic(topicName: String,
                    partitions: Int,
                    replication: Short,
                    kafkaProps: Properties
    )
}