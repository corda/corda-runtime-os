package net.corda.crypto.service.signing

import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException

/**
 * The [SigningServiceFactory] provides operations to create service side services.
 */
interface SigningServiceFactory {
    /**
     * Returns s service instance of the [SigningService].
     *
     * @throws [IllegalStateException] if the factory haven't been started yet
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun getInstance(tenantId: String): SigningService
}