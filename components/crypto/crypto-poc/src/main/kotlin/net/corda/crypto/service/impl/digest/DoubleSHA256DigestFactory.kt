package net.corda.crypto.service.impl.digest

import net.corda.v5.crypto.extensions.DigestAlgorithm
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory

class DoubleSHA256DigestFactory : DigestAlgorithmFactory {
    override val algorithm: String = DoubleSHA256Digest.ALGORITHM
    override fun getInstance(): DigestAlgorithm = DoubleSHA256Digest()
}