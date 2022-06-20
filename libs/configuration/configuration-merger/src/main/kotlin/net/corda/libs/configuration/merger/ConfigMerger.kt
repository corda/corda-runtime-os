package net.corda.libs.configuration.merger

import net.corda.libs.configuration.SmartConfig

/**
 * Handles merging of 2 or more SmartConfigs into each other. e.g merging boot config into messaging config.
 */
interface ConfigMerger {

    /**
     * Merge values from the [bootConfig] into the [messagingConfig] received from the config topic and return the resulting messaging
     * config.
     * @param bootConfig boot config created on startup
     * @param messagingConfig messaging config taken from the topic
     * @return Messaging config with boot config values merged into it.
     */
    fun getMessagingConfig(bootConfig: SmartConfig, messagingConfig: SmartConfig? = null) : SmartConfig

    /**
     * Merge values from the [bootConfig] into the [dbConfig] received from the config topic and return the resulting db
     * config.
     * @param bootConfig boot config created on startup
     * @param dbConfig db config taken from the topic
     * @return DB config with boot config values merged into it.
     */
    fun getDbConfig(bootConfig: SmartConfig, dbConfig: SmartConfig?): SmartConfig

    //TODO - remove this as part of https://r3-cev.atlassian.net/browse/CORE-5086
    /**
     * Merge values from the [bootConfig] into the [cryptoConfig] received from the config topic and return the resulting crypto
     * config.
     * @param bootConfig boot config created on startup
     * @param cryptoConfig crypto config taken from the topic
     * @return Crypto config with boot config values merged into it.
     */
    fun getCryptoConfig(bootConfig: SmartConfig, cryptoConfig: SmartConfig?): SmartConfig
}
