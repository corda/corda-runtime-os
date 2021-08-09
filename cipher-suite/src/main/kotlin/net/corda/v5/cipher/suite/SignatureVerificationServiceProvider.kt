package net.corda.v5.cipher.suite

import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException

/**
 * Factory to create new instances of the key encoding service.
 */
interface SignatureVerificationServiceProvider {
    /**
     * The name used to resolve current provider by crypto library factory.
     */
    val name: String

    /**
     * Returns an instance of the [SignatureVerificationService].
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun getInstance(cipherSuiteFactory: CipherSuiteFactory): SignatureVerificationService
}