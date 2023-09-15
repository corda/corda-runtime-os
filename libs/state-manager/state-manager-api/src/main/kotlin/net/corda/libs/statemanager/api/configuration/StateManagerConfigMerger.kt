package net.corda.libs.statemanager.api.configuration

import net.corda.libs.configuration.SmartConfig

interface StateManagerConfigMerger {

    /**
     * Return a new [SmartConfig] with the state manager configuration from the [bootConfig] merged with any state manager config from
     * the topic in [stateManagerConfig].
     *
     * @param bootConfig configuration object containing boot config
     * @param stateManagerConfig existing state manager configuration from the config topic, can be null
     * @return SmartConfig object containing the state manager configuration.
     */
    fun getStateManagerConfig(bootConfig: SmartConfig, stateManagerConfig: SmartConfig?): SmartConfig
}
