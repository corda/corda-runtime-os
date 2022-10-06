package net.corda.crypto.service

import net.corda.v5.crypto.extensions.DigestAlgorithmFactory

/**
 * Provide a [DigestAlgorithmFactory] for the given algorithm name.
 */
interface DigestAlgorithmFactoryProvider {
    /**
     * Get the [DigestAlgorithmFactory] for the given [algorithmName]
     */
    fun get(algorithmName: String): DigestAlgorithmFactory?
}
