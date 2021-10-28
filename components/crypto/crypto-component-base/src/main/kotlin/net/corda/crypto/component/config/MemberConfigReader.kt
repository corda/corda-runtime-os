package net.corda.crypto.component.config

import net.corda.v5.cipher.suite.config.CryptoMemberConfig
import net.corda.v5.crypto.exceptions.CryptoConfigurationException

/**
 * Defines operations to read member's configuration.
 */
interface MemberConfigReader {
    /**
     * Returns the member config, if the member id is not found then
     * it tries to return the value using 'default' key and if that is not found then
     * it throws [CryptoConfigurationException] exception
     */
    fun get(memberId: String): CryptoMemberConfig
}
