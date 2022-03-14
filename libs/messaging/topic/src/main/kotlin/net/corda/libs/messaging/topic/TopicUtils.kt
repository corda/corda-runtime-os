package net.corda.libs.messaging.topic

import com.typesafe.config.Config


/**
 * Interface for creating topics on the message bus.
 * Instances of this class can be created via [TopicUtilsFactory]
 */
interface TopicUtils {

    /**
     * Create new topics based on [topicsTemplate]
     */
    fun createTopics(topicsTemplate: Config)
}
