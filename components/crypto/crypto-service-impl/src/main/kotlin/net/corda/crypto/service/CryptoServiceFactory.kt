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
    fun getServiceRef(tenantId: String, category: String): CryptoServiceRef

    /**
     * Returns instance of [CryptoService] for specified HSM configuration.
     * Once created the information and instance are cached.
     */
    fun getInstance(configId: String): CryptoService
}
