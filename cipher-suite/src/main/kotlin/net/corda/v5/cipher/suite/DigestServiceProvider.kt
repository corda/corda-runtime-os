package net.corda.v5.cipher.suite

import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException

/**
 * Factory to create new instances of the basic hashing service.
 */
interface DigestServiceProvider {
    /**
     * The name used to resolve current provider by crypto library factory.
     */
    val name: String

    /**
     * Returns an instance of the [DigestService].
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun getInstance(cipherSuiteFactory: CipherSuiteFactory): DigestService
}