package net.corda.configuration.write.publish

import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.reconciliation.ReconcilerWriter

/**
 * Configuration publisher interface.
 *
 * Because it is being used by [ConfigWriteService] which needs boot config, [ConfigPublishService] needs
 * also boot config. It cannot wait for dynamic config because that would mean a deadlock, i.e.
 * [ConfigPublishService] wait on config read service for dynamic config and config read service wait on [ConfigWriteService]
 */
interface ConfigPublishService : ReconcilerWriter<String, Configuration>, Lifecycle {
    /**
     * Publishes a new [ConfigurationDto].
     */
    @Suppress("parameter_name_changed_on_override")
    override fun put(configSection: String, config: Configuration)

    /**
     * Provides boot configuration to config publish service.
     */
    fun bootstrapConfig(bootConfig: SmartConfig)
}