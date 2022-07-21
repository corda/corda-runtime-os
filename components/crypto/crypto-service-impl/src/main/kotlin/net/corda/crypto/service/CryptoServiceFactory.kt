package net.corda.crypto.service

import net.corda.lifecycle.Lifecycle
import net.corda.v5.cipher.suite.CryptoService

/**
 * Provides instances of fully configured [CryptoService].
 */
interface CryptoServiceFactory : Lifecycle {
    /**
     * Returns [CryptoServiceRef] containing [CryptoService] for a given tenant and category.
     * Once created the information and instance are cached.
     */
    fun findInstance(tenantId: String, category: String): CryptoServiceRef

    /**
     * Returns instance of [CryptoService] for specified worker set.
     * Once created the information and instance are cached.
     */
    fun getInstance(workerSetId: String): CryptoService
}
