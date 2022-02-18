package net.corda.configuration.read.impl

import com.typesafe.config.ConfigFactory
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace

internal class ConfigProcessor(
    private val coordinator: LifecycleCoordinator,
    private val smartConfigFactory: SmartConfigFactory,
    private val bootConfig: SmartConfig
) : CompactedProcessor<String, Configuration> {

    private companion object {
        private val logger = contextLogger()
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<Configuration>
        get() = Configuration::class.java

    override fun onSnapshot(currentData: Map<String, Configuration>) {
        if (currentData.isNotEmpty()) {
            val config = currentData.map { Pair(it.key, it.value.toSmartConfig()) }.toMap().toMutableMap()
            // This is a tactical change to ensure that the messaging config always has a default (i.e. the boot config).
            // All config keys should really have some default, but currently there's no way of ensuring this for other
            // keys (and there's not much config for other keys anyway). Longer term we may want to ensure that defaults
            // are always pushed to the config topic, so the workers know to wait until the first config reconciliation
            // has happened.
            val messagingConfig = config[MESSAGING_CONFIG]?.withFallback(bootConfig) ?: bootConfig
            config[MESSAGING_CONFIG] = messagingConfig
            logger.trace { "Initial config snapshot received: $config" }
            coordinator.postEvent(NewConfigReceived(config))
        } else {
            logger.debug { "No initial data to read from configuration topic" }
            val config = mapOf(MESSAGING_CONFIG to bootConfig)
            coordinator.postEvent(NewConfigReceived(config))
        }
    }

    override fun onNext(
        newRecord: Record<String, Configuration>,
        oldValue: Configuration?,
        currentData: Map<String, Configuration>
    ) {
        val config = newRecord.value?.toSmartConfig()
        if (config != null) {
            logger.trace { "New configuration received for key ${newRecord.key}: $config" }
            val configToPush = if (newRecord.key == MESSAGING_CONFIG) {
                config.withFallback(bootConfig)
            } else {
                config
            }
            coordinator.postEvent(NewConfigReceived(mapOf(newRecord.key to configToPush)))
        } else {
            logger.debug { "Received config change event on key ${newRecord.key} with no configuration" }
        }
    }

    private fun Configuration.toSmartConfig(): SmartConfig {
        return smartConfigFactory.create(ConfigFactory.parseString(this.value))
    }
}