package net.corda.libs.messaging.topic.factory

import net.corda.libs.messaging.topic.KafkaTopicUtils
import net.corda.libs.messaging.topic.utils.TopicUtils
import net.corda.libs.messaging.topic.utils.factory.TopicUtilsFactory
import net.corda.messagebus.kafka.utils.OsgiDelegatedClassLoader
import org.apache.kafka.clients.admin.AdminClient
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Component
import java.util.*


/**
 * Kafka implementation of [TopicUtilsFactory]
 * Used to create kafka instances of [TopicUtils]
 */
@Component(service = [TopicUtilsFactory::class])
class KafkaTopicUtilsFactory : TopicUtilsFactory {

    override fun createTopicUtils(props: Properties): TopicUtils {
        val contextClassLoader = Thread.currentThread().contextClassLoader
        val currentBundle = FrameworkUtil.getBundle(KafkaTopicUtils::class.java)

        return try {
            Thread.currentThread().contextClassLoader = OsgiDelegatedClassLoader(currentBundle)
            KafkaTopicUtils(AdminClient.create(props))
        } finally {
            Thread.currentThread().contextClassLoader = contextClassLoader
        }
    }
}
