package net.corda.libs.kafka.topic.utils

import com.typesafe.config.Config


/**
 * Interface for creating Kafka topics
 * Instances of this class can be created via [TopicUtilsFactory]
 */
interface TopicUtils {

    /**
     * Create new topics based on [topicsTemplate]
     */
    fun createTopics(topicsTemplate: Config)
}