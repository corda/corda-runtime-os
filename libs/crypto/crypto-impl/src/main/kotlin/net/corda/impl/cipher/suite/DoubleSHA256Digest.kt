package net.corda.impl.cipher.suite

import net.corda.v5.cipher.suite.DigestAlgorithm
import net.corda.v5.crypto.sha256Bytes

class DoubleSHA256Digest : DigestAlgorithm {
    companion object {
        const val ALGORITHM = "SHA-256D"
    }
    override val algorithm = ALGORITHM
    override val digestLength = 32
    override fun digest(bytes: ByteArray): ByteArray = bytes.sha256Bytes().sha256Bytes()
}