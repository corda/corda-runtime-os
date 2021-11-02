package net.corda.libs.configuration.read.file

import net.corda.libs.configuration.SmartConfig
import java.util.*

class ConfigRepository {

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
