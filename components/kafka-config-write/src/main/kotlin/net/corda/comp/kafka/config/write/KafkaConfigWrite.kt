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
import java.util.*

@Component(immediate = true, service = [KafkaConfigWrite::class])
class KafkaConfigWrite @Activate constructor(
    @Reference(service = ConfigWriteServiceFactory::class)
    private val configWriteServiceFactory: ConfigWriteServiceFactory
) {
    private companion object {
        private val log: Logger = LoggerFactory.getLogger(KafkaConfigWrite::class.java)
    }

    fun updateConfig(destination: String, kafkaProperties: Properties, config: String) {
        val writer = configWriteServiceFactory.createWriteService(destination, kafkaProperties)
        val configuration = ConfigFactory.parseString(config)

        for (packageKey in configuration.root().keys) {
            val packageVersionNumber = ConfigVersionNumber.from(configuration.getString("$packageKey.version"))
            val packageVersion =
                CordaConfigurationVersion(packageKey, packageVersionNumber.major, packageVersionNumber.minor)
            val packageConfig = configuration.getConfig(packageKey)
            for (componentKey in packageConfig.root().keys) {
                if (!componentKey.equals("version")) {
                    val componentVersionNumber =
                        ConfigVersionNumber.from(packageConfig.getString("$componentKey.version"))
                    val componentVersion =
                        CordaConfigurationVersion(
                            componentKey,
                            componentVersionNumber.major,
                            componentVersionNumber.minor
                        )
                    val configurationKey = CordaConfigurationKey(packageKey, packageVersion, componentVersion)
                    writer.updateConfiguration(configurationKey, packageConfig.atKey(componentKey))
                }
            }
        }
    }
}
