package net.corda.libs.configuration.read.kafka

import com.typesafe.config.Config

class ConfigRepository {

    private var configurationMap: Map<String, Config> = mutableMapOf()

    fun getConfigurations(): Map<String, Config> {
        return configurationMap
    }

    fun storeConfiguration(configuration: Map<String, Config>) {
        configurationMap = configurationMap.plus(configuration)
    }

    fun updateConfiguration(key: String, value: Config) {
        configurationMap = configurationMap.plus(Pair(key, value))
    }
}
