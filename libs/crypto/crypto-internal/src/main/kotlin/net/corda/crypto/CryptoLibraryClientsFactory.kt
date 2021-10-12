package net.corda.crypto

import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException

/**
 * The [CryptoLibraryClientsFactory] provides operations to create crypto service clients
 * providing key generation and signing.
 */
interface CryptoLibraryClientsFactory {
    /**
     * Returns a client instance of the [FreshKeySigningService].
     *
     * @throws [IllegalStateException] if the factory haven't been started yet
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun getFreshKeySigningService(): FreshKeySigningService

    /**
     * Returns a client instance of the [SigningService].
     *
     * @throws [IllegalStateException] if the factory haven't been started yet
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun getSigningService(category: String): SigningService
}