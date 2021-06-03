package net.corda.libs.kafka.topic.utils.factory

import net.corda.libs.kafka.topic.utils.TopicUtils
import java.util.*

/**
 * Factory for creating instances of [TopicUtils]
 */
interface TopicUtilsFactory {

    /**
     * @return An instance of [TopicUtils]
     */
    fun createTopicUtils(kafkaProps: Properties): TopicUtils
}