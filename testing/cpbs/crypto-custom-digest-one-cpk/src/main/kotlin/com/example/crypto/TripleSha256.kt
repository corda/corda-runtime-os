package com.example.crypto

import net.corda.v5.crypto.extensions.DigestAlgorithm
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory

/**
 * This class should show up in the jar manifest
 */
class TripleSha256 : DigestAlgorithmFactory {
    override fun getAlgorithm() = TripleSha256Digest.ALGORITHM

    override fun getInstance(): DigestAlgorithm = TripleSha256Digest()
}
