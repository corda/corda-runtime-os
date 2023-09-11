package net.corda.libs.configuration.merger.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.messagebus.api.configuration.BusConfigMerger
import net.corda.messagebus.api.configuration.getConfigOrEmpty
import net.corda.schema.configuration.BootConfig.BOOT_DB
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ConfigMerger::class])
class ConfigMergerImpl @Activate constructor(
    @Reference(service = BusConfigMerger::class)
    private val busConfigMerger: BusConfigMerger
) : ConfigMerger {

    override fun getMessagingConfig(bootConfig: SmartConfig, messagingConfig: SmartConfig?): SmartConfig {
        return busConfigMerger.getMessagingConfig(bootConfig, messagingConfig)
    }

    override fun getDbConfig(bootConfig: SmartConfig, dbConfig: SmartConfig?): SmartConfig {
        //TODO - Boot params for db connection details currently passed in via BOOT_DB.*. Db config logic needs to be
        // migrated to use the defined boot schema values. When that this done they can be merged properly from boot db config here.
        val updatedDbConfig = dbConfig?: SmartConfigImpl.empty()
        val bootDBParamsConfig = bootConfig.getConfigOrEmpty(BOOT_DB)
        return bootDBParamsConfig.withFallback(updatedDbConfig)
    }
}
