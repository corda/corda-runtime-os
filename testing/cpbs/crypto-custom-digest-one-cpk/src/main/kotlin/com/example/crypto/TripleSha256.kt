package com.example.crypto

import net.corda.v5.cipher.suite.DigestAlgorithm
import net.corda.v5.cipher.suite.DigestAlgorithmFactory

/**
 * This class should show up in the jar manifest
 */
class TripleSha256 : DigestAlgorithmFactory {
    override val algorithm: String
        get() = TripleSha256Digest.ALGORITHM

    override fun getInstance(): DigestAlgorithm  = TripleSha256Digest()
}
