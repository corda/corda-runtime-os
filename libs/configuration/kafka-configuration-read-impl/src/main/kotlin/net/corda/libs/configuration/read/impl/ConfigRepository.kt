package net.corda.libs.configuration.read.impl

import com.typesafe.config.Config
import net.corda.libs.configuration.read.impl.processor.ConfigCompactedProcessor
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.*

@Component(immediate = true, service = [ConfigRepository::class])
class ConfigRepository(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
) {

    private val CONFIGURATION_READ_SERVICE = "CONFIGURATION_READ_SERVICE"

    fun getConfiguration(topicName: String, kafkaProperties: Properties): Map<String, Config> {
        val compactedSubscription =
            subscriptionFactory.createCompactedSubscription(
                SubscriptionConfig(CONFIGURATION_READ_SERVICE, topicName),
                ConfigCompactedProcessor(),
                kafkaProperties.map
            )
    }
}