package net.corda.crypto

import net.corda.data.crypto.config.HSMConfig
import net.corda.lifecycle.Lifecycle

/**
 * The HSM registration client to generate keys.
 */
interface HSMRegistrationClient : Lifecycle {
    /**
     * Adds new HSM configuration.
     */
    fun putHSM(config: HSMConfig): CryptoPublishResult

    /**
     * Assigns hardware backed HSM to the given tenant.
     */
    fun assignHSM(
        tenantId: String,
        category: String,
        defaultSignatureScheme: String
    ): CryptoPublishResult

    /**
     * Assigns soft HSM to the given tenant.
     */
    fun assignSoftHSM(
        tenantId: String,
        category: String,
        passphrase: String,
        defaultSignatureScheme: String
    ): CryptoPublishResult
}