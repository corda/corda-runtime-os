package net.corda.crypto.service

import net.corda.crypto.CryptoLibraryFactory
import net.corda.crypto.SigningService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException

/**
 * The [CryptoLibraryFactory] provides operations to create service side services.
 */
interface CryptoFactory {

    /**
     * Return an instance of the [CipherSchemeMetadata]
     */
    val cipherSchemeMetadata: CipherSchemeMetadata

    /**
     * Returns a service instance of the [FreshKeySigningService].
     *
     * @throws [IllegalStateException] if the factory haven't been started yet
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun getFreshKeySigningService(memberId: String): FreshKeySigningService

    /**
     * Returns s service instance of the [SigningService].
     *
     * @throws [IllegalStateException] if the factory haven't been started yet
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun getSigningService(memberId: String, category: String): SigningService
}