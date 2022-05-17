package net.corda.messagebus.api.configuration

import net.corda.libs.configuration.SmartConfig

/**
 * Handles merging of 2 configs into each other. e.g merging boot config into messaging config.
 */
interface BusConfigMerger {

    /**
     * Merge values from the [bootConfig] into the [messagingConfig] received from the config topic and return the resulting messaging
     * config.
     * @param bootConfig boot config created on startup
     * @param messagingConfig messaging config take from the topic. Can be null on boot for initial connection to kafka.
     * @return Messaging config with boot config values merged into it.
     */
    fun getMessagingConfig(bootConfig: SmartConfig, messagingConfig: SmartConfig?) : SmartConfig
}
