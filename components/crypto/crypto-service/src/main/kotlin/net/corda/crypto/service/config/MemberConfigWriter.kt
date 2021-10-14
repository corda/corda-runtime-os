package net.corda.crypto.service.config

import net.corda.v5.cipher.suite.config.CryptoMemberConfig
import net.corda.v5.crypto.exceptions.CryptoConfigurationException

/**
 * Defines operations to write member's configuration.
 */
interface MemberConfigWriter {
    /**
     * Persist the specified member's configuration.
     */
    fun put(memberId: String, entity: CryptoMemberConfig)
}
