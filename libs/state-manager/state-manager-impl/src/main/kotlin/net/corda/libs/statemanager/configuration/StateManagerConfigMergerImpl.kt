package net.corda.libs.statemanager.configuration

import com.typesafe.config.ConfigValueFactory
import org.slf4j.LoggerFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.schema.configuration.BootConfig.BOOT_STATE_MANAGER
import net.corda.schema.configuration.BootConfig.BOOT_STATE_MANAGER_TYPE
import org.osgi.service.component.annotations.Component

@Component(service = [StateManagerConfigMerger::class])
class StateManagerConfigMergerImpl : StateManagerConfigMerger {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun getStateManagerConfig(bootConfig: SmartConfig, stateManagerConfig: SmartConfig?): SmartConfig {
        logger.info("Merging boot config into state manager config")
//        val root = SmartConfigImpl.empty()
//            .withValue(BOOT_STATE_MANAGER_TYPE, ConfigValueFactory.fromAnyRef("DATABASE"))
//        root.withValue(BOOT_STATE_MANAGER, ConfigValueFactory.fromAnyRef())
        val updatedConfig = stateManagerConfig ?: SmartConfigImpl.empty()
        val bootParamsConfig = bootConfig.getConfigOrEmpty(BOOT_STATE_MANAGER)
        return bootParamsConfig.withFallback(updatedConfig)
    }
}
fun SmartConfig.getConfigOrEmpty(path: String): SmartConfig = if (hasPath(path)) getConfig(path) else SmartConfigImpl.empty()