package net.corda.libs.configuration.read.kafka

import net.corda.libs.configuration.SmartConfig
import java.util.*

class ConfigRepository(bootstrapConfig: SmartConfig) {

    companion object {
        const val BOOTSTRAP_KEY = "corda.boot"
    }

    private var configurationMap: Map<String, SmartConfig> = Collections.synchronizedMap(mutableMapOf())

    fun getConfigurations(): Map<String, SmartConfig> {
        return configurationMap.toMap()
    }

    fun storeConfiguration(configuration: Map<String, SmartConfig>) {
        configurationMap = configurationMap.plus(configuration)
    }

    fun updateConfiguration(key: String, value: SmartConfig) {
        configurationMap = configurationMap.plus(Pair(key, value))
    }
}
