package net.corda.crypto.service

import net.corda.v5.cipher.suite.CryptoService

/**
 * Provides instances of fully configured [CryptoService].
 */
interface CryptoServiceFactory {
    /**
     * Returns [CryptoServiceConfiguredInstance] containing instance of [CryptoService]
     * for specified tenant and category. Once created the information and instance are cached.
     */
    fun getInstance(tenantId: String, category: String): CryptoServiceConfiguredInstance
}

