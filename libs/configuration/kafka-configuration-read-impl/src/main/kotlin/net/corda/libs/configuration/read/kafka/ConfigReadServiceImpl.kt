package net.corda.libs.configuration.read.kafka

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.data.config.Configuration
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.ConfigReadService
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.osgi.service.component.annotations.Component
import java.util.*

@Suppress("TooGenericExceptionCaught")
@Component(immediate = true, service = [ConfigReadService::class])
class ConfigReadServiceImpl(
    private val configurationRepository: ConfigRepository,
    private val subscriptionFactory: SubscriptionFactory,
) : ConfigReadService, CompactedProcessor<String, Configuration> {


    @Volatile
    private var stopped = false
    private val CONFIGURATION_READ_SERVICE = "CONFIGURATION_READ_SERVICE"
    private var configUpdates = mutableListOf<ConfigListener>()
    private lateinit var subscription: CompactedSubscription<String, Configuration>

    override val isRunning: Boolean
        get() {
            return !stopped
        }

    override fun start() {
        configUpdates = Collections.synchronizedList(mutableListOf<ConfigListener>())
        subscription =
            subscriptionFactory.createCompactedSubscription(
                SubscriptionConfig(
                    CONFIGURATION_READ_SERVICE,
                    ConfigFactory.load("kafka.properties").getString("topic.name")
                ),
                this,
                mapOf()
            )
        subscription.start()
        stopped = false
    }

    override fun stop() {
        configUpdates = mutableListOf()
        subscription.stop()
        stopped = true
    }

    override fun registerCallback(configListener: ConfigListener): Int {
        configUpdates.add(configListener)
        configListener.onUpdate(setOf(), configurationRepository.getConfigurations())
        return configUpdates.lastIndex
    }

    override fun unregisterCallback(callbackId: Int) {
        configUpdates.removeAt(callbackId)
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<Configuration>
        get() = Configuration::class.java

    override fun onSnapshot(currentData: Map<String, Configuration>) {
        val configMap = mutableMapOf<String, Config>()
        for (config in currentData) {
            configMap[config.key] = ConfigFactory.parseString(config.value.value)
        }
        configurationRepository.storeConfiguration(configMap)
        configUpdates.forEach { it.onUpdate(setOf(), configurationRepository.getConfigurations()) }
    }

    override fun onNext(
        newRecord: Record<String, Configuration>,
        oldValue: Configuration?,
        currentData: Map<String, Configuration>
    ) {
        val config = ConfigFactory.parseString(newRecord.value?.value)
        configurationRepository.updateConfiguration(newRecord.key, config)
        configUpdates.forEach { it.onUpdate(setOf(newRecord.key), configurationRepository.getConfigurations()) }

    }
}
