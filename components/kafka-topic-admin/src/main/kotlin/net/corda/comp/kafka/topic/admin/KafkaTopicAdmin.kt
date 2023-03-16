package net.corda.comp.kafka.topic.admin

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.libs.messaging.topic.utils.factory.TopicUtilsFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.*

@Component(immediate = true, service = [KafkaTopicAdmin::class])
class KafkaTopicAdmin @Activate constructor(
    @Reference(service = TopicUtilsFactory::class)
    private val topicUtilsFactory: TopicUtilsFactory
) {

    fun createTopics(kafkaConnectionProperties: Properties, topicTemplate: String): Config {
        topicUtilsFactory.createTopicUtils(kafkaConnectionProperties).use {topicUtils ->
            val template = ConfigFactory.parseString(topicTemplate)
            topicUtils.createTopics(template)

            return template
        }
    }
}
