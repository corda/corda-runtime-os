package net.corda.libs.configuration.merger.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.messagebus.api.configuration.BusConfigMerger
import net.corda.messagebus.api.configuration.getConfigOrEmpty
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ConfigMerger::class])
class ConfigMergerImpl @Activate constructor(
    @Reference(service = BusConfigMerger::class)
    private val busConfigMerger: BusConfigMerger
) : ConfigMerger {

    override fun getConfig(bootConfig: SmartConfig, configKey: String, existingConfig: SmartConfig?): SmartConfig {
        val updatedConfig = existingConfig ?: SmartConfigImpl.empty()
        val bootConfiguration = bootConfig.getConfigOrEmpty(configKey)

        return bootConfiguration.withFallback(updatedConfig)
    }

    override fun getMessagingConfig(bootConfig: SmartConfig, messagingConfig: SmartConfig?): SmartConfig {
        return busConfigMerger.getMessagingConfig(bootConfig, messagingConfig)
    }
}
