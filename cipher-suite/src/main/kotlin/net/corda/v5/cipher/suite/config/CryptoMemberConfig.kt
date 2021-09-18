package net.corda.v5.cipher.suite.config

import net.corda.v5.crypto.exceptions.CryptoConfigurationException

/**
 * Defines a member configuration. It expects that there should be at least one entry with the "default" key
 * the rest of the keys are categories, e.g. LEDGER, FRESH_KEYS, TLS, etc.
 */
interface CryptoMemberConfig {
    companion object {
        /**
         * The key which is used for the categories which don't have their own specific configuration.
         */
        const val DEFAULT_CATEGORY_KEY = "default"
    }

    /**
     * The default configuration which wil be used if the 'category' specific key is not specified.
     */
    val default: CryptoServiceConfig

    /**
     * Returns the service configuration for specified category, if the category key is not found it'll return the
     * configuration with the 'default' key or throws [CryptoConfigurationException] exception if the default
     * is not specified
     */
    fun getCategory(category: String): CryptoServiceConfig
}