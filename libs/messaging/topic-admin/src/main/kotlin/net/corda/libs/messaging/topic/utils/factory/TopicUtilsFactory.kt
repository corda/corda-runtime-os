package net.corda.libs.messaging.topic.utils.factory

import net.corda.libs.messaging.topic.utils.TopicUtils
import java.util.*

/**
 * Factory for creating instances of [TopicUtils]
 */
interface TopicUtilsFactory {

    /**
     * @return An instance of [TopicUtils]
     */
    fun createTopicUtils(props: Properties): TopicUtils
}
