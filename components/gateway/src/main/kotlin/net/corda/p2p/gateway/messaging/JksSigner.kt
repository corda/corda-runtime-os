package net.corda.p2p.gateway.messaging

import net.corda.crypto.delegated.signing.DelegatedSigner
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

internal class JksSigner(
    private val publicKeyToPrivateKey: Map<PublicKey, PrivateKey>
) : DelegatedSigner {

    private val provider by lazy {
        BouncyCastleProvider()
    }

    override fun sign(
        publicKey: PublicKey,
        algorithm: String,
        data: ByteArray
    ): ByteArray {
        val privateKey = publicKeyToPrivateKey[publicKey] ?: throw SecurityException("Could not find private key")
        val signature = Signature.getInstance(algorithm, provider)
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }
}
