package net.corda.libs.messaging.topic.factory

import com.typesafe.config.Config
import net.corda.libs.messaging.topic.TopicUtils
import java.util.*

/**
 * Factory for creating instances of [TopicUtils]
 */
interface TopicUtilsFactory {

    /**
     * @return An instance of [TopicUtils]
     */
    fun createTopicUtils(config: Config): TopicUtils

    /**
     * @return An instance of [TopicUtils]
     */
    fun createTopicUtils(props: Properties): TopicUtils
}
