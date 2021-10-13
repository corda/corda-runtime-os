package net.corda.crypto

import net.corda.v5.cipher.suite.DigestAlgorithm
import net.corda.v5.cipher.suite.DigestAlgorithmFactory

/**
 * Provider which provides custom digest implementations which should be implemented as a pair of
 * [DigestAlgorithmFactory] and [DigestAlgorithm], the former instantiating the latter.
 * It's expected that the custom implementations would be placed to CPI.
 */
interface DigestAlgorithmFactoryProvider {
    fun factories(): List<DigestAlgorithmFactory>
}