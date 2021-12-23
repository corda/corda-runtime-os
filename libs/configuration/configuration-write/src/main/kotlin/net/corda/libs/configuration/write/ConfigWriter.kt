package net.corda.libs.configuration.write

import com.typesafe.config.Config

@Suppress("Deprecation")
@Deprecated("Use `PersistentConfigWriter` instead.")
interface ConfigWriter {
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