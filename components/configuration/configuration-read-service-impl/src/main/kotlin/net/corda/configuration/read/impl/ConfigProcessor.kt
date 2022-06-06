package net.corda.configuration.read.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import java.util.concurrent.ConcurrentHashMap
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.reconciliation.VersionedRecord
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.DB_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.RECONCILIATION_CONFIG
import net.corda.schema.configuration.ConfigKeys.RPC_CONFIG
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

// This should be used by our class that needs to cache config

internal class ConfigProcessor(
    private val coordinator: LifecycleCoordinator,
    private val smartConfigFactory: SmartConfigFactory,
    private val bootConfig: SmartConfig,
    private val configMerger: ConfigMerger
) : CompactedProcessor<String, Configuration> {

    private companion object {
        private val logger = contextLogger()
    }

    private val configCache = ConcurrentHashMap<String, VersionedRecord<String, Configuration>>()

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<Configuration>
        get() = Configuration::class.java

    override fun onSnapshot(currentData: Map<String, Configuration>) {
        if (currentData.isNotEmpty()) {
            currentData.forEach { (configSection, configuration) ->
                addToCache(configSection, configuration)
            }

            val config = mergeConfigs(currentData)
            coordinator.postEvent(NewConfigReceived(config))
        } else {
            logger.debug { "No initial data to read from configuration topic" }
            val config = mapOf(MESSAGING_CONFIG to configMerger.getMessagingConfig(bootConfig, null))
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
            logger.info(
                "Received configuration for key $newConfigKey: " +
                    newConfig.toSafeConfig().root().render(ConfigRenderOptions.concise().setFormatted(true))
            )
            coordinator.postEvent(NewConfigReceived(mapOf(newConfigKey to config.getConfig(newConfigKey))))
        } else {
            logger.debug { "Received config change event on key ${newRecord.key} with no configuration" }
        }
    }

    private fun mergeConfigs(currentData: Map<String, Configuration>): MutableMap<String, SmartConfig> {
        return if (currentData.isNotEmpty()) {
            val config = currentData.mapValues { config ->
                config.value.toSmartConfig().also { smartConfig ->
                    logger.info(
                        "Received configuration for key ${config.key}: " +
                                smartConfig.toSafeConfig().root().render(ConfigRenderOptions.concise().setFormatted(true))
                    )
                }
            }.toMutableMap()
            config[MESSAGING_CONFIG] = configMerger.getMessagingConfig(bootConfig, config[MESSAGING_CONFIG])
            config[DB_CONFIG] = configMerger.getDbConfig(bootConfig, config[DB_CONFIG])
            //TODO - remove the following three calls when defaulting via reconciliation process is possible. The following calls only
            // exist to preserve defaulting logic present
            config[RPC_CONFIG] = configMerger.getRPCConfig(bootConfig, config[RPC_CONFIG])
            config[RECONCILIATION_CONFIG] = configMerger.getReconciliationConfig(bootConfig, config[RECONCILIATION_CONFIG])
            config[CRYPTO_CONFIG] = configMerger.getCryptoConfig(bootConfig, config[CRYPTO_CONFIG])
            config
        } else {
            mutableMapOf()
        }
    }

    private fun Configuration.toSmartConfig(): SmartConfig {
        return smartConfigFactory.create(ConfigFactory.parseString(this.value))
    }

    // TODO - this should be made to return added config value (did not exist or version is higher than existing or deleted,
    //  so ignore lesser versions), and then only these should be propagated to registered components.
    private fun addToCache(configSection: String, configuration: Configuration?) {
        if (configuration != null) {
            val versionedRecord = object : VersionedRecord<String, Configuration> {
                override val version = configuration.version.toInt()
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