package net.corda.crypto.clients

import net.corda.data.crypto.config.HSMConfig

/**
 * The HSM registration client to generate keys.
 */
interface HSMRegistrarClient {
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