package net.corda.crypto.service

import net.corda.lifecycle.Lifecycle
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException

/**
 * The [SigningServiceFactory] provides operations to create service side services.
 */
interface SigningServiceFactory : Lifecycle {
    /**
     * Returns s service instance of the [SigningService].
     *
     * @throws [IllegalStateException] if the factory haven't been started yet
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun getInstance(tenantId: String): SigningService
}