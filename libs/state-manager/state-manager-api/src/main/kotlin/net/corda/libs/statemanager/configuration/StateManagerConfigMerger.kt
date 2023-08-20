package net.corda.libs.statemanager.configuration

import net.corda.libs.configuration.SmartConfig

/**
 * Handles merging of two configs into each other. e.g merging boot config into state manager config.
 */
interface StateManagerConfigMerger {

    /**
     * Merge values from the [bootConfig] into the [stateManagerConfig] received from the config topic and return the resulting stateManager
     * config.
     * @param bootConfig boot config created on startup
     * @param stateManagerConfig messaging config take from the topic. Can be null on boot for initial connection to kafka.
     * @return State manager config with boot config values merged into it.
     */
    fun getStateManagerConfig(bootConfig: SmartConfig, stateManagerConfig: SmartConfig?) : SmartConfig
}
