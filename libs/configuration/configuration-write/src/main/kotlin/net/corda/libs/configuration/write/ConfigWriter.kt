package net.corda.libs.configuration.write

import com.typesafe.config.Config

@Suppress("Deprecation")
@Deprecated("Use `PersistentConfigWriter` instead.")
interface ConfigWriter {
    /**
     * When appending, the storage layer is checked for existing configuration for the given [configKey].
     * If any exist it will be retrieved. The given [config] object will be merged with the existing one.
     * The new properties will be added. Properties that exist in both configuration objects will have their value
     * updated to the new one. Old properties will be kept.
     * The merged configuration object is then persisted
     *
     * @param configKey used to uniquely identify the config
     * @param config Changes to be recorded
     */
    fun appendConfiguration(
        configKey: CordaConfigurationKey,
        config: Config
    )

    /**
     * When updating, the stored configuration object for the [configKey] will be completely replaced by the given [config] object
     *
     * @param configKey used to uniquely identify the config
     * @param config Changes to be recorded
     */
    fun updateConfiguration(
        configKey: CordaConfigurationKey,
        config: Config
    )
}