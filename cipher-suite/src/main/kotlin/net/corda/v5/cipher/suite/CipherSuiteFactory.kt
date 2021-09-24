package net.corda.v5.cipher.suite

import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException

/**
 * The [CipherSuiteFactory] provides operations to create various cipher suite's services.
 */
interface CipherSuiteFactory {

    /**
     * Returns an instance of the [CipherSchemeMetadata].
     *
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun getSchemeMap(): CipherSchemeMetadata

    /**
     * Returns an instance of the [SignatureVerificationService].
     *
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun getSignatureVerificationService(): SignatureVerificationService

    /**
     * Returns an instance of the [DigestService].
     *
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun getDigestService(): DigestService
}