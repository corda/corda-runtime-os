package net.corda.p2p.linkmanager.common

import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import net.corda.utilities.publicKeyFactory
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

internal class PublicKeyReader {
    companion object {
        fun PublicKey.toKeyAlgorithm(): KeyAlgorithm {
            return when (this.algorithm) {
                "EC", "ECDSA" -> KeyAlgorithm.ECDSA
                "RSA" -> KeyAlgorithm.RSA
                else -> throw UnsupportedAlgorithm(this.algorithm)
            }
        }

        internal fun KeyAlgorithm.getSignatureSpec(): SignatureSpec {
            return when (this) {
                KeyAlgorithm.RSA -> SignatureSpec.RSA_SHA256
                KeyAlgorithm.ECDSA -> SignatureSpec.ECDSA_SHA256
            }
        }
    }
    fun loadPublicKey(pem: String): PublicKey {
        return publicKeyFactory(pem.reader()) ?: throw InvalidPem(pem)
    }
    class InvalidPem(pem: String) : Exception("Invalid public key PEM: $pem")
    class UnsupportedAlgorithm(algorithm: String) : Exception("Unsupported algorithm $algorithm")
}
