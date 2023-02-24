package net.corda.libs.messaging.topic.utils

import com.typesafe.config.Config
import java.io.Closeable


/**
 * Interface for creating Kafka topics
 * Instances of this class can be created via [TopicUtilsFactory]
 */
interface TopicUtils: Closeable {

    /**
     * Create new topics based on [topicsTemplate]
     */
    fun createTopics(topicsTemplate: Config)
}
