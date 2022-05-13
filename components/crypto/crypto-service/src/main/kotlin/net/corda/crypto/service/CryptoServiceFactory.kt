package net.corda.crypto.service

import net.corda.lifecycle.Lifecycle
import net.corda.v5.cipher.suite.CryptoService

/**
 * Provides instances of fully configured [CryptoService].
 */
interface CryptoServiceFactory : Lifecycle {
    /**
     * Returns [CryptoServiceRef] containing instance of [CryptoService]
     * for specified tenant and category. Once created the information and instance are cached.
     */
    fun getInstance(tenantId: String, category: String): CryptoServiceRef

    /**
     * Returns instance of [CryptoService] for specified HSM configuration.
     * Once created the information and instance are cached.
     */
    fun getInstance(configId: String): CryptoService

    /**
     * Returns [CryptoServiceRef] containing instance of [CryptoService]
     * for specified association id. Once created the information and instance are cached.
     *
     * @param tenantId the tenant owning the association, really it's here only to validate that the recorded
     * association belongs to the tenant.
     * @param category the category, really it's here only to validate that the recorded
     * association is for that category.
     * * @param associationId the id if the tenant's association with the HSM
     */
    fun getInstance(tenantId: String, category: String, associationId: String): CryptoServiceRef
}
