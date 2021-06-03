package net.corda.comp.kafka.topic.admin

import net.corda.data.kafka.KafkaTopicTemplate
import net.corda.libs.kafka.topic.utils.factory.TopicUtilsFactory
import org.apache.avro.io.DecoderFactory
import org.apache.avro.specific.SpecificDatumReader
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.StringReader
import java.util.*

@Component
class KafkaTopicAdmin @Activate constructor(
    @Reference(service = TopicUtilsFactory::class)
    private val topicUtilsFactory: TopicUtilsFactory
) {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(KafkaTopicAdmin::class.java)
    }

    fun createTopic(props: String, topicTemplate: String): KafkaTopicTemplate{
        val topicUtils = topicUtilsFactory.createTopicUtils(parseProperties(props))
        val template = parseTopicTemplate(topicTemplate)
        topicUtils.createTopic(template)

        return template
    }

    private fun parseProperties(props: String): Properties {
        val properties = Properties()
        properties.load(StringReader(props))
        return properties
    }

    private fun parseTopicTemplate(template: String): KafkaTopicTemplate {
        val reader = SpecificDatumReader(KafkaTopicTemplate::class.java)
        var templateObject = KafkaTopicTemplate()
        try {
            val decoder = DecoderFactory.get().jsonDecoder(KafkaTopicTemplate.getClassSchema(), template)
            templateObject = reader.read(null, decoder)
        } catch (e: IOException) {
            log.error("Error deserialising given template: ${e.message}")
        }
        return templateObject
    }


}