package net.corda.v5.cipher.suite.config

import net.corda.v5.crypto.exceptions.CryptoConfigurationException

/**
 * Defines the minimum crypto library configuration, the keys are mix of member ids and other configuration
 * such as RPC, cache, etc., the latter values are internal to the crypto library implementation.
 */
interface CryptoLibraryConfig : Map<String, Any?> {
    companion object {
        /**
         * The key which is used for the members which don't have their own specific configuration.
         */
        const val DEFAULT_MEMBER_KEY = "default"
    }

    /**
     * Returns the member config, if the member id is not found then
     * it tries to return the value using 'default' key and if that is not found then
     * it throws [CryptoConfigurationException] exception
     */
    fun getMember(memberId: String): CryptoMemberConfig
}

/**
 * Similar ot typesafe's hasPath where it returns true if the key is present, and it's not null
 */
fun Map<String, Any?>.hasPath(key: String) : Boolean = get(key) != null
