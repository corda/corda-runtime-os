package net.corda.configuration.read.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.reconciliation.VersionedRecord
import net.corda.schema.configuration.BootConfig.BOOT_DB
import net.corda.schema.configuration.BootConfig.BOOT_STATE_MANAGER
import net.corda.schema.configuration.ConfigKeys.DB_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.STATE_MANAGER_CONFIG
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

// This should be used by our class that needs to cache config

internal class ConfigProcessor(
    private val coordinator: LifecycleCoordinator,
    private val smartConfigFactory: SmartConfigFactory,
    private val bootConfig: SmartConfig,
    private val configMerger: ConfigMerger
) : CompactedProcessor<String, Configuration> {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val configCache = ConcurrentHashMap<String, VersionedRecord<String, Configuration>>()

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<Configuration>
        get() = Configuration::class.java

    override fun onSnapshot(currentData: Map<String, Configuration>) {
        val config = mergeConfigs(currentData)

        if (config.values.any { !it.isEmpty }) {
            currentData.forEach { (configKey, configuration: Configuration) ->
                addToCache(configKey, configuration)

                logger.info("Received initial configuration for key $configKey")
                logger.debug {
                    "$configKey configuration: " +
                            config[configKey]!!.toSafeConfig().root()
                                .render(ConfigRenderOptions.concise().setFormatted(true))
                }
            }

            coordinator.postEvent(NewConfigReceived(config))
        }
    }

    override fun onNext(
        newRecord: Record<String, Configuration>,
        oldValue: Configuration?,
        currentData: Map<String, Configuration>
    ) {
        addToCache(newRecord.key, newRecord.value)

        val newConfig = newRecord.value?.toSmartConfig()
        if (newConfig != null) {
            val config = mergeConfigs(currentData)
            val newConfigKey = newRecord.key
            logger.info("Received new configuration for key $newConfigKey")
            logger.debug {
                "$newConfigKey configuration: " +
                        newConfig.toSafeConfig().root().render(ConfigRenderOptions.concise().setFormatted(true))
            } // this can dump out all the configuration including the defaults
            coordinator.postEvent(NewConfigReceived(mapOf(newConfigKey to config.getConfig(newConfigKey))))
        } else {
            logger.debug { "Received config change event on key ${newRecord.key} with no configuration" }
        }
    }

    fun get(section: String): Configuration? {
        return configCache[section]?.value
    }

    fun getSmartConfig(section: String): SmartConfig? {
        return get(section)?.toSmartConfig()
    }

    private fun mergeConfigs(currentData: Map<String, Configuration>): MutableMap<String, SmartConfig> {
        val config = currentData.mapValues { config ->
            config.value.toSmartConfig()
        }.toMutableMap()

        if (currentData.containsKey(MESSAGING_CONFIG)) {
            config[MESSAGING_CONFIG] = configMerger.getMessagingConfig(bootConfig, config[MESSAGING_CONFIG])
        }

        val dbConfig = configMerger.getConfig(bootConfig, BOOT_DB, config[DB_CONFIG])
        if (!dbConfig.isEmpty) {
            config[DB_CONFIG] = dbConfig
        }

        val stateManagerConfig = configMerger.getConfig(bootConfig, BOOT_STATE_MANAGER, config[STATE_MANAGER_CONFIG])
        if (!stateManagerConfig.isEmpty) {
            config[STATE_MANAGER_CONFIG] = stateManagerConfig
        }

        // at this point config is fully populated and verified
        return config
    }

    private fun Configuration.toSmartConfig(): SmartConfig {
        return smartConfigFactory.create(ConfigFactory.parseString(this.value))
    }

    // TODO - this should be made to return added config value (did not exist or version is higher than existing or deleted,
    //  so ignore lesser versions), and then only these should be propagated to registered components.
    private fun addToCache(configSection: String, configuration: Configuration?) {
        if (configuration != null) {
            val versionedRecord = object : VersionedRecord<String, Configuration> {
                override val version = configuration.version
                override val isDeleted = false
                override val key = configSection
                override val value: Configuration = configuration
            }
            configCache[configSection] = versionedRecord
        } else {
            configCache.remove(configSection)
        }
    }

    internal fun getAllVersionedRecords() = configCache.values.stream()
}
