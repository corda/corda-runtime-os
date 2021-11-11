package org.example.crypto

import net.corda.v5.cipher.suite.DigestAlgorithm
import net.corda.v5.cipher.suite.DigestAlgorithmFactory

/**
 * This class should show up in the jar manifest
 */
class QuadSha256 : DigestAlgorithmFactory {
    override val algorithm: String
        get() = QuadSha256Digest.ALGORITHM

    override fun getInstance(): DigestAlgorithm  = QuadSha256Digest()
}
