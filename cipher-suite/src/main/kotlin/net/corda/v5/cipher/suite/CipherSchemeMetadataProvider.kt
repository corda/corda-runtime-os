package net.corda.v5.cipher.suite

import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException

/**
 * Factory to create new instances of the cipher scheme map service.
 */
interface CipherSchemeMetadataProvider {
    /**
     * The name used to resolve current provider by crypto library factory.
     */
    val name: String

    /**
     * Returns an instance of the [CipherSchemeMetadata].
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun getInstance(): CipherSchemeMetadata
}