package net.corda.libs.statemanager.impl.configuration

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.statemanager.api.configuration.StateManagerConfigMerger
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.MessagingConfig
import org.osgi.service.component.annotations.Component

@Component(service = [StateManagerConfigMerger::class])
class StateManagerConfigMergerImpl : StateManagerConfigMerger {
    override fun getStateManagerConfig(bootConfig: SmartConfig, stateManagerConfig: SmartConfig?): SmartConfig {
        var updatedConfig = stateManagerConfig ?: SmartConfigImpl.empty()

        bootConfig.getConfigOrEmpty(BootConfig.BOOT_STATE_MANAGER).entrySet().forEach { entry ->
            updatedConfig = updatedConfig.withValue(
                "${MessagingConfig.StateManager.STATE_MANAGER}.${entry.key}",
                fromAnyRef(bootConfig.getString("${BootConfig.BOOT_STATE_MANAGER}.${entry.key}"))
            )
        }
        return updatedConfig
    }

    private fun SmartConfig.getConfigOrEmpty(path: String): SmartConfig = if (hasPath(path)) getConfig(path) else SmartConfigImpl.empty()
}