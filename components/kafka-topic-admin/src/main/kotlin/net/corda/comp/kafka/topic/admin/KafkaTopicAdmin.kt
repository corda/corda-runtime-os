package net.corda.comp.kafka.topic.admin

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.libs.kafka.topic.utils.factory.TopicUtilsFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.StringReader
import java.util.*

@Component(immediate = true, service = [KafkaTopicAdmin::class])
class KafkaTopicAdmin @Activate constructor(
    @Reference(service = TopicUtilsFactory::class)
    private val topicUtilsFactory: TopicUtilsFactory
) {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(KafkaTopicAdmin::class.java)
    }

    fun createTopic(props: String, topicTemplate: String): Config {
        val topicUtils = topicUtilsFactory.createTopicUtils(parseProperties(props))
        val template = ConfigFactory.parseString(topicTemplate)
        topicUtils.createTopic(template)

        return template
    }

    private fun parseProperties(props: String): Properties {
        val properties = Properties()
        properties.load(StringReader(props))
        return properties
    }
}
