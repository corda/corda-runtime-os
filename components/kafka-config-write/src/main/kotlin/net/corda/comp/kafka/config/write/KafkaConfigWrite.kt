package net.corda.comp.kafka.config.write

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.write.ConfigVersionNumber
import net.corda.libs.configuration.write.CordaConfigurationKey
import net.corda.libs.configuration.write.CordaConfigurationVersion
import net.corda.libs.configuration.write.factory.ConfigWriteServiceFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component
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
            val packageVersionNumber = ConfigVersionNumber.from(configuration.getString("$key1.version"))
            val packageVersion = CordaConfigurationVersion(key1, packageVersionNumber.major, packageVersionNumber.minor)
            val key1Config = configuration.getConfig(key1)
            for (key2 in key1Config.root().keys) {
                val componentVersionNumber = ConfigVersionNumber.from(configuration.getString("$key1.$key2.version"))
                val componentVersion =
                    CordaConfigurationVersion(key2, componentVersionNumber.major, componentVersionNumber.minor)
                val configurationKey = CordaConfigurationKey(key1, packageVersion, componentVersion)
                writer.updateConfiguration(configurationKey, key1Config.atKey(key2))
            }
        }
    }
}
