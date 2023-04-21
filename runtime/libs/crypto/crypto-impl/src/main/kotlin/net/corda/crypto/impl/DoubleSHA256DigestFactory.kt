package net.corda.crypto.impl

import net.corda.v5.crypto.extensions.DigestAlgorithm
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory

class DoubleSHA256DigestFactory : DigestAlgorithmFactory {
    override fun getAlgorithm(): String = DoubleSHA256Digest.ALGORITHM
    override fun getInstance(): DigestAlgorithm = DoubleSHA256Digest()
}