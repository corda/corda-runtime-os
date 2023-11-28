package net.corda.libs.configuration.merger

import net.corda.libs.configuration.SmartConfig

/**
 * Handles merging of 2 or more SmartConfigs into each other, e.g. merging boot config into messaging config.
 */
interface ConfigMerger {

    /**
     * Merge values from configuration section under [configKey] path within [bootConfig] into the [existingConfig]
     * received from the configuration topic, and return the merged configuration.
     *
     * @param bootConfig boot config created on startup
     * @param configKey path for the boot configuration section to merge
     * @param existingConfig existing configuration taken from the topic
     * @return configuration with boot config values under the specified section merged into it
     */
    fun getConfig(bootConfig: SmartConfig, configKey: String, existingConfig: SmartConfig?): SmartConfig

    /**
     * Merge values from the [bootConfig] into the [messagingConfig] received from the config topic and return the resulting messaging
     * config.
     * @param bootConfig boot config created on startup
     * @param messagingConfig messaging config taken from the topic
     * @return Messaging config with boot config values merged into it.
     */
    fun getMessagingConfig(bootConfig: SmartConfig, messagingConfig: SmartConfig? = null): SmartConfig
}
