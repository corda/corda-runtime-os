package net.corda.libs.configuration.read.kafka.processor

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.data.config.Configuration
import net.corda.libs.configuration.read.ConfigUpdate
import net.corda.libs.configuration.read.kafka.ConfigRepository
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class ConfigCompactedProcessor(
    private val configRepository: ConfigRepository
) : CompactedProcessor<String, Configuration> {
    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val configUpdates = Collections.synchronizedList(mutableListOf<ConfigUpdate>())

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
        configUpdates.forEach { it.onUpdate(configMap) }
    }

    override fun onNext(
        newRecord: Record<String, Configuration>,
        oldValue: Configuration?,
        currentData: Map<String, Configuration>
    ) {
        val config = ConfigFactory.parseString(newRecord.value?.value)
        configRepository.updateConfiguration(newRecord.key, config)
        configUpdates.forEach { it.onUpdate(mapOf(newRecord.key to config)) }

    }

    fun registerCallback(configUpdate: ConfigUpdate) {
        configUpdates.add(configUpdate)
    }

}