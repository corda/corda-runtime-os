package net.corda.internal.crypto

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException

/**
 * The [CryptoLibraryFactory] provides operations to create various crypto services.
 */
interface CryptoLibraryFactory {
    /**
     * Returns an instance of the [FreshKeySigningService].
     *
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun getFreshKeySigningService(
        passphrase: String,
        defaultSchemeCodeName: String,
        freshKeysDefaultSchemeCodeName: String
    ): FreshKeySigningService

    /**
     * Returns an instance of the [SigningService].
     *
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun getSigningService(
        category: String,
        passphrase: String,
        defaultSchemeCodeName: String
    ): SigningService

    /**
     * Returns an instance of the [SignatureVerificationService].
     *
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun getSignatureVerificationService(): SignatureVerificationService

    /**
     * Returns an instance of the [KeyEncodingService].
     *
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun getKeyEncodingService(): KeyEncodingService

    /**
     * Returns an instance of the [CipherSchemeMetadata].
     *
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun getCipherSchemeMetadata(): CipherSchemeMetadata

    /**
     * Returns an instance of [DigestService].
     *
     */
    fun getDigestService(): DigestService
}