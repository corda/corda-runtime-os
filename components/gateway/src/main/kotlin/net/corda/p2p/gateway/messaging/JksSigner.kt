package net.corda.p2p.gateway.messaging

import net.corda.crypto.delegated.signing.DelegatedSigner
import net.corda.v5.crypto.SignatureSpec
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

internal class JksSigner(
    private val publicKeyToPrivateKey: Map<PublicKey, PrivateKey>
) : DelegatedSigner {
    override fun sign(publicKey: PublicKey, spec: SignatureSpec, data: ByteArray): ByteArray {
        val privateKey = publicKeyToPrivateKey[publicKey] ?: throw SecurityException("Could not find private key")
        val providerName = when (publicKey.algorithm) {
            "RSA" -> "SunRsaSign"
            "EC" -> "SunEC"
            else -> throw SecurityException("Unsupported algorithm ${publicKey.algorithm}")
        }
        val signature = Signature.getInstance(
            spec.signatureName,
            providerName
        )
        signature.initSign(privateKey)
        signature.setParameter(spec.params)
        signature.update(data)
        return signature.sign()
    }
}
