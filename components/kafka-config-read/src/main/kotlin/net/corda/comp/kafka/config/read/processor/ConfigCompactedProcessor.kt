package net.corda.comp.kafka.config.read.processor

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.data.config.Configuration
import net.corda.libs.configuration.read.ConfigRepository
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ConfigCompactedProcessor(
    private val configRepository: ConfigRepository
) : CompactedProcessor<String, Configuration> {
    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<Configuration>
        get() = Configuration::class.java

    override fun onSnapshot(currentData: Map<String, Configuration>) {
        val configMap = mutableMapOf<String, Config>()
        for(config in currentData) {
            configMap[config.key] = ConfigFactory.parseString(config.value.value)
        }
        configRepository.storeConfiguration(configMap)
    }

    override fun onNext(
        newRecord: Record<String, Configuration>,
        oldValue: Configuration?,
        currentData: Map<String, Configuration>
    ) {
        configRepository.updateConfiguration(newRecord.key, ConfigFactory.parseString(newRecord.value?.value))
    }
}