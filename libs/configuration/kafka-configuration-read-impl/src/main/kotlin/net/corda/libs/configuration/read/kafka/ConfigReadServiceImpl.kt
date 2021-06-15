package net.corda.libs.configuration.read.kafka

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.read.ConfigReadService
import net.corda.libs.configuration.read.ConfigUpdate
import net.corda.libs.configuration.read.kafka.processor.ConfigCompactedProcessor
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.osgi.service.component.annotations.Component

@Suppress("TooGenericExceptionCaught")
@Component(immediate = true, service = [ConfigReadService::class])
class ConfigReadServiceImpl(
    private val configurationRepository: ConfigRepository,
    private val subscriptionFactory: SubscriptionFactory
) :
    ConfigReadService {

    private val CONFIGURATION_READ_SERVICE = "CONFIGURATION_READ_SERVICE"
    private val processor = ConfigCompactedProcessor(configurationRepository)

    companion object {
        private val objectMapper = ObjectMapper()
            .registerModule(KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    override fun start() {
        val compactedSubscription =
            subscriptionFactory.createCompactedSubscription(
                SubscriptionConfig(
                    CONFIGURATION_READ_SERVICE,
                    ConfigFactory.load("kafka.properties").getString("topic.name")
                ),
                processor,
                mapOf()
            )
        compactedSubscription.start()
    }

    override fun getConfiguration(componentName: String): Config {
        return configurationRepository.getConfigurations()[componentName]
            ?: throw IllegalArgumentException("Unknown component: $componentName")
    }

    override fun getAllConfiguration(): Map<String, Config> {
        return configurationRepository.getConfigurations()
    }

    override fun <T> parseConfiguration(componentName: String, clazz: Class<T>): T {
        return try {
            val config = getConfiguration(componentName)
            objectMapper.convertValue(config, clazz)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Throwable) {
            throw IllegalArgumentException("Cannot deserialize configuration for $clazz", e)
        }
    }

    override fun registerCallback(configUpdate: ConfigUpdate) {
        processor.registerCallback(configUpdate)
    }
}
