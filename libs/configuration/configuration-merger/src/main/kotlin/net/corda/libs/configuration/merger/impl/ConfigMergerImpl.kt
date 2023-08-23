package net.corda.libs.configuration.merger.impl

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.messagebus.api.configuration.BusConfigMerger
import net.corda.messagebus.api.configuration.getConfigOrEmpty
import net.corda.messagebus.api.configuration.getStringOrNull
import net.corda.schema.configuration.BootConfig.BOOT_DB
import net.corda.schema.configuration.BootConfig.BOOT_STATE_MANAGER_JDBC_URL
import net.corda.schema.configuration.BootConfig.BOOT_STATE_MANAGER_PASS
import net.corda.schema.configuration.BootConfig.BOOT_STATE_MANAGER_USER
import net.corda.schema.configuration.StateManagerConfig
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

    override fun getStateManagerConfig(bootConfig: SmartConfig, stateStorageConfig: SmartConfig?): SmartConfig {
        val updatedConfig = stateStorageConfig ?: SmartConfigImpl.empty()
        return updatedConfig
            .withValue(StateManagerConfig.TYPE, ConfigValueFactory.fromAnyRef("DATABASE"))
            .withValue(StateManagerConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(bootConfig.getStringOrNull(BOOT_STATE_MANAGER_JDBC_URL)))
            .withValue(StateManagerConfig.DB_USER, ConfigValueFactory.fromAnyRef(bootConfig.getStringOrNull(BOOT_STATE_MANAGER_USER)))
            .withValue(StateManagerConfig.DB_PASS, ConfigValueFactory.fromAnyRef(bootConfig.getStringOrNull(BOOT_STATE_MANAGER_PASS)))
    }
}