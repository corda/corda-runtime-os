package net.corda.crypto.client

import net.corda.data.crypto.config.HSMInfo
import net.corda.lifecycle.Lifecycle

/**
 * The HSM registration client to generate keys.
 */
interface HSMRegistrationClient : Lifecycle {
    /**
     * Assigns hardware backed HSM to the given tenant.
     *
     * @param tenantId the tenant which HSM should be assigned to.
     *
     * @return info object about the assigned HSM.
     *
     * @throws CryptoLibraryException if the category is not supported by any
     */
    fun assignHSM(
        tenantId: String,
        category: String
    ): HSMInfo

    /**
     * Assigns soft HSM to the given tenant.
     */
    fun assignSoftHSM(
        tenantId: String,
        category: String,
        passphrase: String
    ): HSMInfo

    /**
     * Looks up information about the assigned HSM.
     *
     * @return The HSM's info if it's assigned otherwise null.
     */
    fun findHSM(tenantId: String, category: String) : HSMInfo?
}