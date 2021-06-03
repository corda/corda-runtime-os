package net.corda.libs.kafka.topic.utils.impl.factory

import net.corda.libs.kafka.topic.utils.TopicUtils
import net.corda.libs.kafka.topic.utils.factory.TopicUtilsFactory
import net.corda.libs.kafka.topic.utils.impl.KafkaTopicUtils
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