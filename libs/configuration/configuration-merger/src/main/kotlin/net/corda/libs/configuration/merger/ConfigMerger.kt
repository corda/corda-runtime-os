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

    //TODO - remove the following three calls when defaulting via reconciliation process is possible. The following calls only
    // exist to preserve defaulting logic present
    /**
     * Merge values from the [bootConfig] into the [rpcConfig] received from the config topic and return the resulting rpc
     * config.
     * @param bootConfig boot config created on startup
     * @param rpcConfig RPC config taken from the topic
     * @return RPC config with boot config values merged into it.
     */
    fun getRPCConfig(bootConfig: SmartConfig, rpcConfig: SmartConfig?) : SmartConfig

    /**
     * Merge values from the [bootConfig] into the [reconciliation] received from the config topic and return the resulting reconciliation
     * config.
     * @param bootConfig boot config created on startup
     * @param reconciliation reconciliation config taken from the topic
     * @return Reconciliation config with boot config values merged into it.
     */
    fun getReconciliationConfig(bootConfig: SmartConfig, reconciliation: SmartConfig?) : SmartConfig

    /**
     * Merge values from the [bootConfig] into the [cryptoConfig] received from the config topic and return the resulting crypto
     * config.
     * @param bootConfig boot config created on startup
     * @param cryptoConfig crypto config taken from the topic
     * @return Crypto config with boot config values merged into it.
     */
    fun getCryptoConfig(bootConfig: SmartConfig, cryptoConfig: SmartConfig?): SmartConfig
}
