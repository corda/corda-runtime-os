package net.corda.libs.messaging.topic.factory

import com.typesafe.config.Config
import net.corda.libs.messaging.topic.KafkaTopicUtils
import net.corda.libs.messaging.topic.utils.TopicUtils
import net.corda.libs.messaging.topic.utils.factory.TopicUtilsFactory
import org.apache.kafka.clients.admin.AdminClient
import org.osgi.service.component.annotations.Component
import java.util.*


/**
 * Kafka implementation of [TopicUtilsFactory]
 * Used to create kafka instances of [TopicUtils]
 */
@Component(service = [TopicUtilsFactory::class])
class KafkaTopicUtilsFactory : TopicUtilsFactory {

    override fun createTopicUtils(props: Properties): TopicUtils {
        return KafkaTopicUtils(AdminClient.create(props))
    }

    override fun createTopicUtils(config: Config): TopicUtils {
        val props = Properties()
        props.putAll(config.entrySet().associate { (key, value) -> key to value.unwrapped() })
        return createTopicUtils(props)
    }
}
