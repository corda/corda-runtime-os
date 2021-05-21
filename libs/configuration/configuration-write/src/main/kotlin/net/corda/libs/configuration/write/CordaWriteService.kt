package net.corda.libs.configuration.write

import com.typesafe.config.Config

interface CordaWriteService {
    /**
     * Add the properties recorded in [config] to the component configuration.
     *
     * @param key [CordaConfigurationKey] object used to uniquely identify the config
     * @param config Changes to be recorded
     */
    fun appendConfiguration(
            key: CordaConfigurationKey,
            config: Config
    )

    /**
     * Update the component configuration so that it matches [config].
     *
     * @param key [CordaConfigurationKey] object used to uniquely identify the config
     * @param config Changes to be recorded
     */
    fun updateConfiguration(
            key: CordaConfigurationKey,
            config: Config
    )
}