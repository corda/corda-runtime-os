package net.corda.libs.messaging.topic.factory

import net.corda.libs.messaging.topic.KafkaTopicUtils
import net.corda.libs.messaging.topic.utils.TopicUtils
import net.corda.libs.messaging.topic.utils.factory.TopicUtilsFactory
import org.apache.kafka.clients.admin.AdminClient
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.BundleWiring
import org.osgi.service.component.annotations.Component
import java.util.Properties

/**
 * Kafka implementation of [TopicUtilsFactory]
 * Used to create kafka instances of [TopicUtils]
 */
@Component(service = [TopicUtilsFactory::class])
class KafkaTopicUtilsFactory : TopicUtilsFactory {

    override fun createTopicUtils(props: Properties): TopicUtils {
        val contextClassLoader = Thread.currentThread().contextClassLoader
        val currentBundle = FrameworkUtil.getBundle(AdminClient::class.java)

        return if (currentBundle != null) {
            try {
                Thread.currentThread().contextClassLoader = currentBundle.adapt(BundleWiring::class.java).classLoader
                KafkaTopicUtils(AdminClient.create(props))
            } finally {
                Thread.currentThread().contextClassLoader = contextClassLoader
            }
        } else {
            KafkaTopicUtils(AdminClient.create(props))
        }
    }
}
