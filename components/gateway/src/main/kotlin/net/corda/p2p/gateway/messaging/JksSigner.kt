package net.corda.p2p.gateway.messaging

import net.corda.crypto.delegated.signing.DelegatedSigner
import net.corda.crypto.delegated.signing.DelegatedSignerInstaller
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

internal class JksSigner(
    private val publicKeyToPrivateKey: Map<PublicKey, PrivateKey>
) : DelegatedSigner {
    private fun signEc(
        privateKey: PrivateKey,
        hash: DelegatedSigner.Hash,
        data: ByteArray
    ): ByteArray {
        val signature = Signature.getInstance(
            hash.ecName,
            "SunEC"
        )
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    private fun signRsa(
        privateKey: PrivateKey,
        hash: DelegatedSigner.Hash,
        data: ByteArray
    ): ByteArray {
        val signature = Signature.getInstance(
            DelegatedSignerInstaller.RSA_SIGNING_ALGORITHM,
            "SunRsaSign"
        )
        val parameter = hash.rsaParameter
        signature.initSign(privateKey)
        signature.setParameter(parameter)
        signature.update(data)
        return signature.sign()
    }

    override fun sign(
        publicKey: PublicKey,
        hash: DelegatedSigner.Hash,
        data: ByteArray
    ): ByteArray {
        val privateKey = publicKeyToPrivateKey[publicKey] ?: throw SecurityException("Could not find private key")

        return when (publicKey.algorithm) {
            "RSA" -> signRsa(privateKey, hash, data)
            "EC" -> signEc(privateKey, hash, data)
            else -> throw SecurityException("Unsupported algorithm ${publicKey.algorithm}")
        }
    }
}
