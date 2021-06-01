package net.corda.libs.kafka.topic.utils

import net.corda.data.kafka.KafkaTopicTemplate
import java.util.*


/**
 * Interface for creating Kafka topics
 * Instances of this class can be created via [TopicUtilsFactory]
 */
interface TopicUtils {

    /**
     * Create new topic based on [topicTemplate]
     */
    fun createTopic(topicTemplate: KafkaTopicTemplate)

}