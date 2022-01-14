package net.corda.configuration.read.impl

import com.typesafe.config.ConfigFactory
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace

internal class ConfigProcessor(
    private val smartConfigFactory: SmartConfigFactory,
    private val coordinator: LifecycleCoordinator
) : CompactedProcessor<String, Configuration> {

    private companion object {
        private val logger = contextLogger()
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<Configuration>
        get() = Configuration::class.java

    override fun onSnapshot(currentData: Map<String, Configuration>) {
        val config = currentData.map { Pair(it.key, it.value.toSmartConfig()) }.toMap()
        logger.trace { "Initial config snapshot received: $config" }
        coordinator.postEvent(NewConfigReceived(config))
    }

    override fun onNext(
        newRecord: Record<String, Configuration>,
        oldValue: Configuration?,
        currentData: Map<String, Configuration>
    ) {
        val config = newRecord.value?.toSmartConfig()
        if (config != null) {
            logger.trace { "New configuration received for key ${newRecord.key}: $config" }
            coordinator.postEvent(NewConfigReceived(mapOf(newRecord.key to config)))
        } else {
            logger.debug { "Received config change event on key ${newRecord.key} with no configuration" }
        }
    }

    private fun Configuration.toSmartConfig(): SmartConfig {
        return smartConfigFactory.create(ConfigFactory.parseString(this.value))
    }
}