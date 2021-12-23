@file:Suppress("DEPRECATION")

package net.corda.configuration.publish.impl

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.configuration.publish.ConfigPublishService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.publish.ConfigPublisher
import net.corda.libs.configuration.publish.CordaConfigurationKey
import net.corda.libs.configuration.publish.CordaConfigurationVersion
import net.corda.libs.configuration.publish.factory.ConfigPublisherFactory
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(immediate = true, service = [ConfigPublishService::class])
class ConfigPublishServiceImpl @Activate constructor(
    @Reference(service = ConfigPublisherFactory::class)
    private val configPublisherFactory: ConfigPublisherFactory
): ConfigPublishService {
    private lateinit var publisher: ConfigPublisher

    private companion object {
        private val log: Logger = contextLogger()
    }

    override fun updateConfig(destination: String, appConfig: SmartConfig, configurationFile: String) {
        publisher = configPublisherFactory.createPublisher(destination, appConfig)
        val configuration = ConfigFactory.parseString(configurationFile)

        for (packageKey in configuration.root().keys) {
            var packageVersion: CordaConfigurationVersion
            try {
                packageVersion =
                    CordaConfigurationVersion.from(packageKey, configuration.getString("$packageKey.packageVersion"))
            } catch (e: ConfigException) {
                log.warn(
                    "Package $packageKey has no defined packageVersion. " +
                            "Discarding package configuration"
                )
                continue
            }

            val packageConfig = configuration.getConfig(packageKey)
            for (componentKey in packageConfig.root().keys) {
                //skip if the component key is the package version
                if (componentKey != "packageVersion") {
                    writeComponentConfig(componentKey, packageConfig, packageKey, packageVersion)
                }
            }
        }
    }

    private fun writeComponentConfig(
        componentKey: String,
        packageConfig: Config,
        packageKey: String,
        packageVersion: CordaConfigurationVersion
    ) {
        try {
            val componentVersion =
                CordaConfigurationVersion.from(
                    componentKey,
                    packageConfig.getString("$componentKey.componentVersion")
                )
            val configurationKey = CordaConfigurationKey(packageKey, packageVersion, componentVersion)
            publisher.updateConfiguration(configurationKey, packageConfig.getConfig(componentKey))
        } catch (e: ConfigException) {
            log.warn(
                "Component $componentKey has no defined componentVersion. " +
                        "Discarding component configuration"
            )
        }
    }
}
