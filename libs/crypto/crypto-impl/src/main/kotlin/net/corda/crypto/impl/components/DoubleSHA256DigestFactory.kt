package net.corda.crypto.impl.components

import net.corda.v5.cipher.suite.DigestAlgorithm
import net.corda.v5.cipher.suite.DigestAlgorithmFactory

class DoubleSHA256DigestFactory : DigestAlgorithmFactory {
    override val algorithm: String = DoubleSHA256Digest.ALGORITHM
    override fun getInstance(): DigestAlgorithm = DoubleSHA256Digest()
}