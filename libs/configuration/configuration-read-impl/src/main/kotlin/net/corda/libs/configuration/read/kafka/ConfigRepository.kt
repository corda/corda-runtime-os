package net.corda.libs.configuration.read.kafka

import com.typesafe.config.Config
import java.util.*

class ConfigRepository(bootstrapConfig: Config) {

    companion object {
        const val BOOTSTRAP_KEY = "corda.boot"
    }

    private var configurationMap: Map<String, Config> = Collections.synchronizedMap(mutableMapOf(BOOTSTRAP_KEY to bootstrapConfig))

    fun getConfigurations(): Map<String, Config> {
        return configurationMap.toMap()
    }

    fun storeConfiguration(configuration: Map<String, Config>) {
        configurationMap = configurationMap.plus(configuration)
    }

    fun updateConfiguration(key: String, value: Config) {
        configurationMap = configurationMap.plus(Pair(key, value))
    }
}
