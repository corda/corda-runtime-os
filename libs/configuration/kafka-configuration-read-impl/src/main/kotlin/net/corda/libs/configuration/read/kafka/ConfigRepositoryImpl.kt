package net.corda.libs.configuration.read.kafka

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigRepository
import org.osgi.service.component.annotations.Component

@Component(immediate = true, service = [ConfigRepository::class])
class ConfigRepositoryImpl: ConfigRepository {

    private var configurationMap: Map<String, Config> = mutableMapOf()

    override fun getConfigurations(): Map<String, Config> {
        return configurationMap
    }

    override fun storeConfiguration(configuration: Map<String, Config>) {
        configurationMap = configurationMap.plus(configuration)
    }

    override fun updateConfiguration(key: String, value: Config) {
        configurationMap = configurationMap.plus(Pair(key, value))
    }

}