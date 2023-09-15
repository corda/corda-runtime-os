package net.corda.libs.statemanager.impl.configuration

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messagebus.api.configuration.StateManagerConfigMerger
import net.corda.messagebus.api.configuration.getConfigOrEmpty
import net.corda.schema.configuration.BootConfig
import org.osgi.service.component.annotations.Component

@Component(service = [StateManagerConfigMerger::class])
class StateManagerConfigMergerImpl : StateManagerConfigMerger {
    override fun getStateManagerConfig(bootConfig: SmartConfig, messagingConfig: SmartConfig?): SmartConfig {
        var updatedConfig = messagingConfig ?: SmartConfigImpl.empty()

        bootConfig.getConfigOrEmpty(BootConfig.BOOT_STATE_MANAGER).entrySet().forEach { entry ->
            updatedConfig = updatedConfig.withValue(
                "${BootConfig.BOOT_STATE_MANAGER}.${entry.key}",
                fromAnyRef(bootConfig.getString("${BootConfig.BOOT_STATE_MANAGER}.${entry.key}"))
            )
        }
        return updatedConfig
    }
}