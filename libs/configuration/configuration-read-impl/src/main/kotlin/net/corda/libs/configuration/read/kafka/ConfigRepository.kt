package net.corda.libs.configuration.read.kafka

import com.typesafe.config.Config
import java.util.*

class ConfigRepository {

    private var configurationMap: Map<String, Config> = Collections.synchronizedMap(mutableMapOf())

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
