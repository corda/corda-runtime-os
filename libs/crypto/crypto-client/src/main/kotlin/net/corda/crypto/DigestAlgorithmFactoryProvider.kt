package net.corda.crypto

import net.corda.v5.cipher.suite.DigestAlgorithmFactory

/**
 * Provide a [DigestAlgorithmFactory] for the given algorithm name.
 */
interface DigestAlgorithmFactoryProvider {
    /**
     * Get the [DigestAlgorithmFactory] for the given [algorithmName]
     */
    fun get(algorithmName: String): DigestAlgorithmFactory?
}
