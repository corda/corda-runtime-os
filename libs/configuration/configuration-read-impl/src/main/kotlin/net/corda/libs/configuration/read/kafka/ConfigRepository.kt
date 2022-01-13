package net.corda.libs.configuration.read.kafka

import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.ConfigKeys.Companion.BOOT_CONFIG
import java.util.Collections

class ConfigRepository(bootstrapConfig: SmartConfig) {
    private var configurationMap: Map<String, SmartConfig> =
        Collections.synchronizedMap(mutableMapOf(BOOT_CONFIG to bootstrapConfig))

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
