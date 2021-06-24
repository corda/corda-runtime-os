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
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Suppress("TooGenericExceptionCaught")
class ConfigReadServiceImpl(
    private val configurationRepository: ConfigRepository,
    private val subscriptionFactory: SubscriptionFactory,
    private val boostrapConfig: Config
) : ConfigReadService, CompactedProcessor<String, Configuration> {

    private companion object {
        const val CONFIG_TOPIC_PATH = "config.topic.name"
    }

    @Volatile
    private var stopped = false

    @Volatile
    private var snapshotReceived = false

    private val lock = ReentrantLock()
    private val CONFIGURATION_READ_SERVICE = "CONFIGURATION_READ_SERVICE"
    private val configUpdates = Collections.synchronizedMap(mutableMapOf<ConfigListenerSubscription, ConfigListener>())
    private var subscription: CompactedSubscription<String, Configuration>? = null

    override val isRunning: Boolean
        get() {
            return !stopped
        }

    override fun start() {
        lock.withLock {
            if (subscription == null) {
                subscription =
                    subscriptionFactory.createCompactedSubscription(
                        SubscriptionConfig(
                            CONFIGURATION_READ_SERVICE,
                            boostrapConfig.getString(CONFIG_TOPIC_PATH)
                        ),
                        this,
                        boostrapConfig
                    )
                subscription!!.start()
                stopped = false
            }
        }
    }

    override fun stop() {
        lock.withLock {
            if (!stopped) {
                subscription?.stop()
                subscription = null
                configUpdates.clear()
                stopped = true
                snapshotReceived = false
            }
        }
    }

    override fun registerCallback(configListener: ConfigListener): AutoCloseable {
        val sub = ConfigListenerSubscription(this)
        configUpdates[sub] = configListener
        if (snapshotReceived) {
            val configs = configurationRepository.getConfigurations()
            configListener.onUpdate(configs.keys, configs)
        }
        return sub
    }

    private fun unregisterCallback(sub: ConfigListenerSubscription) {
        configUpdates.remove(sub)
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
        snapshotReceived = true
        val tempConfigMap = configurationRepository.getConfigurations()
        configUpdates.forEach { it.value.onUpdate(tempConfigMap.keys, tempConfigMap) }
    }

    override fun onNext(
        newRecord: Record<String, Configuration>,
        oldValue: Configuration?,
        currentData: Map<String, Configuration>
    ) {
        val config = ConfigFactory.parseString(newRecord.value?.value)
        configurationRepository.updateConfiguration(newRecord.key, config)
        val tempConfigMap = configurationRepository.getConfigurations()
        configUpdates.forEach { it.value.onUpdate(setOf(newRecord.key), tempConfigMap) }

    }

    private class ConfigListenerSubscription(private val configReadService: ConfigReadServiceImpl) : AutoCloseable {
        override fun close() {
            configReadService.unregisterCallback(this)
        }
    }
}


