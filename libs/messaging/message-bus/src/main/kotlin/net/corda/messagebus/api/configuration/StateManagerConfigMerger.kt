package net.corda.messagebus.api.configuration

import net.corda.libs.configuration.SmartConfig

interface StateManagerConfigMerger {

    /**
     * Return a new [SmartConfig] with the state manager configuration from the [bootConfig] merged with any state manager config from
     * the existing [messagingConfig].
     *
     * @param bootConfig configuration object containing boot config
     * @param messagingConfig existing configuration object containing messaging config, or null
     * @return SmartConfig object containing the state manager configuration.
     */
    fun getStateManagerConfig(bootConfig: SmartConfig, messagingConfig: SmartConfig?) : SmartConfig
}
