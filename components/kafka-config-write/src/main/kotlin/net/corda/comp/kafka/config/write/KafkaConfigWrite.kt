package net.corda.comp.kafka.config.write

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.write.CordaConfigurationKey
import net.corda.libs.configuration.write.CordaConfigurationVersion
import net.corda.libs.configuration.write.factory.ConfigWriteServiceFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component(immediate = true, service = [KafkaConfigWrite::class])
class KafkaConfigWrite @Activate constructor(
    @Reference(service = ConfigWriteServiceFactory::class)
    private val configWriteServiceFactory: ConfigWriteServiceFactory
) {
    private companion object {
        private val log: Logger = LoggerFactory.getLogger(KafkaConfigWrite::class.java)
    }

    fun updateConfig(destination: String, config: String) {
        val writer = configWriteServiceFactory.createWriteService(destination)
        val configuration = ConfigFactory.parseString(config)

        for (key1 in configuration.root().keys) {
            val packageVersion = CordaConfigurationVersion(key1, configuration.getString("$key1.version"))
            val key1Config = configuration.getConfig(key1)
            for (key2 in key1Config.root().keys) {
                if (!key2.equals("version")) {
                    val componentVersion = CordaConfigurationVersion(key2, key1Config.getString("$key2.version"))
                    val configurationKey = CordaConfigurationKey(key1, packageVersion, componentVersion)
                    writer.updateConfiguration(configurationKey, key1Config.atKey(key2))
                }
            }
        }
    }
}
