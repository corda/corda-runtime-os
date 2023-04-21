package org.example.crypto

import net.corda.v5.crypto.extensions.DigestAlgorithm
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory

/**
 * This class should show up in the jar manifest
 */
class QuadSha256 : DigestAlgorithmFactory {
    override fun getAlgorithm() = QuadSha256Digest.ALGORITHM

    override fun getInstance(): DigestAlgorithm = QuadSha256Digest()
}
