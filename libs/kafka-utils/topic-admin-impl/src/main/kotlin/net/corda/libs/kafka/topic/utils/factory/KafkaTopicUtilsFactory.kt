package net.corda.libs.kafka.topic.utils.factory

import net.corda.libs.kafka.topic.utils.KafkaTopicUtils
import net.corda.libs.kafka.topic.utils.TopicUtils
import org.apache.kafka.clients.admin.AdminClient
import org.osgi.service.component.annotations.Component
import java.util.*


/**
 * Kafka implementation of [TopicUtilsFactory]
 * Used to create kafka instances of [TopicUtils]
 */
@Component
class KafkaTopicUtilsFactory : TopicUtilsFactory {

    override fun createTopicUtils(kafkaProps: Properties): TopicUtils {
        return KafkaTopicUtils(AdminClient.create(kafkaProps))
    }
}