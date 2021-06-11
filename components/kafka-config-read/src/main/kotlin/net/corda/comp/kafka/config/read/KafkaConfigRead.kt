package net.corda.comp.kafka.config.read

import com.typesafe.config.Config
import net.corda.comp.kafka.config.read.processor.ConfigCompactedProcessor
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import net.corda.libs.configuration.read.factory.ConfigRepositoryFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

@Component(immediate = true, service = [KafkaConfigRead::class])
class KafkaConfigRead @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ConfigRepositoryFactory::class)
    private val repositoryFactory: ConfigRepositoryFactory,
    @Reference(service = ConfigReadServiceFactory::class)
    private val readServiceFactory: ConfigReadServiceFactory

) {
    private companion object{
        private val logger: Logger = LoggerFactory.getLogger(KafkaConfigRead::class.java)
    }

    private val configRepository = repositoryFactory.createRepository()
    private val configReadService = readServiceFactory.createReadService(configRepository)
    private val CONFIGURATION_READ_SERVICE = "CONFIGURATION_READ_SERVICE"

    fun startSubscription(topicName: String, kafkaProperties: Properties) {
        val propertiesMap = mutableMapOf<String, String>()
        kafkaProperties.forEach { (k, v) -> propertiesMap[k.toString()] = v.toString() }
        val compactedSubscription =
            subscriptionFactory.createCompactedSubscription(
                SubscriptionConfig(CONFIGURATION_READ_SERVICE, topicName),
                ConfigCompactedProcessor(configRepository),
                propertiesMap
            )
        compactedSubscription.start()
    }

    fun getAllConfigurations(): Map<String, Config> {
        return configRepository.getConfigurations()
    }

    fun getConfiguration(key: String): Config {
        return configReadService.getConfiguration(key)
    }

}
