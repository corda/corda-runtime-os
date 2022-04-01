package net.corda.p2p.linkmanager

import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import net.corda.p2p.test.stub.crypto.processor.UnsupportedAlgorithm
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC
import net.corda.v5.cipher.suite.schemes.RSA_SHA256_SIGNATURE_SPEC
import net.corda.v5.crypto.SignatureSpec
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.security.PublicKey

internal class PublicKeyReader {
    companion object {
        internal fun PublicKey.toKeyAlgorithm(): KeyAlgorithm {
            return when (this.algorithm) {
                "EC" -> KeyAlgorithm.ECDSA
                "RSA" -> KeyAlgorithm.RSA
                else -> throw UnsupportedAlgorithm(this)
            }
        }

        internal fun KeyAlgorithm.getSignatureSpec(): SignatureSpec {
            return when (this) {
                KeyAlgorithm.RSA -> RSA_SHA256_SIGNATURE_SPEC
                KeyAlgorithm.ECDSA -> ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC
            }
        }
    }
    internal fun loadPublicKey(pem: String): PublicKey {
        return PEMParser(pem.reader()).use { parser ->
            generateSequence {
                parser.readObject()
            }.map {
                if (it is SubjectPublicKeyInfo) {
                    JcaPEMKeyConverter().getPublicKey(it)
                } else {
                    null
                }
            }.filterNotNull()
                .firstOrNull()
        } ?: throw InvalidPem(pem)
    }
    internal class InvalidPem(pem: String) : Exception("Invalid public key PEM: $pem")
}
