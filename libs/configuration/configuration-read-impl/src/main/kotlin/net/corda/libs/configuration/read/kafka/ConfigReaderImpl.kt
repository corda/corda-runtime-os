package net.corda.libs.configuration.read.kafka

import com.typesafe.config.ConfigFactory
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.ConfigReader
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import java.util.Collections
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ConfigReaderImpl(
    private val configurationRepository: ConfigRepository,
    private val subscriptionFactory: SubscriptionFactory,
    private val boostrapConfig: SmartConfig,
    private val smartConfigFactory: SmartConfigFactory,
) : ConfigReader, CompactedProcessor<String, Configuration> {

    companion object {
        internal const val CONFIGURATION_READER = "CONFIGURATION_READER"
    }

    @Volatile
    private var stopped = false

    @Volatile
    private var snapshotReceived = false

    private val lock = ReentrantLock()
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
                            CONFIGURATION_READER,
                            CONFIG_TOPIC
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
                stopped = true
                snapshotReceived = false
            }
        }
    }

    override fun close() {
        lock.withLock {
            stop()
            configUpdates.clear()
        }
    }

    override fun registerCallback(configListener: ConfigListener): AutoCloseable {
        /*
         * A lock is used here as a temporary fix.
         * The start/restart semantics will be revisited and this will be addressed properly as part of CORE-2759 (also see CORE-2781).
         */
        lock.withLock {
            val sub = ConfigListenerSubscription(this)
            configUpdates[sub] = configListener
            if (snapshotReceived) {
                val configs = configurationRepository.getConfigurations()
                configListener.onUpdate(configs.keys, configs)
            }
            return sub
        }
    }

    private fun unregisterCallback(sub: ConfigListenerSubscription) {
        configUpdates.remove(sub)
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<Configuration>
        get() = Configuration::class.java

    override fun onSnapshot(currentData: Map<String, Configuration>) {
        val configMap = mutableMapOf<String, SmartConfig>()
        for (config in currentData) {
            configMap[config.key] = smartConfigFactory.create(
                ConfigFactory.parseString(config.value.value))
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
        val config = smartConfigFactory.create(
            ConfigFactory.parseString(newRecord.value?.value))
        configurationRepository.updateConfiguration(newRecord.key, config)
        val tempConfigMap = configurationRepository.getConfigurations()
        configUpdates.forEach { it.value.onUpdate(setOf(newRecord.key), tempConfigMap) }

    }

    private class ConfigListenerSubscription(private val configReadService: ConfigReaderImpl) : AutoCloseable {
        override fun close() {
            configReadService.unregisterCallback(this)
        }
    }
}


