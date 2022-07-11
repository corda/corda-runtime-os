package net.corda.crypto.ecdh.impl

import java.security.KeyPair
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import javax.crypto.KeyAgreement

class EphemeralKeyPair(
    private val provider: Provider,
    private val keyPair: KeyPair,
    otherPublicKey: PublicKey,
    digestName: String
) : AbstractECDHKeyPair(keyPair.public, otherPublicKey, digestName) {
    companion object {
        fun deriveSharedSecret(provider: Provider, privateKey: PrivateKey, otherPublicKey: PublicKey): ByteArray {
            require(otherPublicKey.algorithm == privateKey.algorithm) {
                "Keys must use the same algorithm"
            }
            return when (privateKey.algorithm) {
                "EC" -> {
                    KeyAgreement.getInstance("ECDH", provider).apply {
                        init(privateKey)
                        doPhase(otherPublicKey, true)
                    }.generateSecret()
                }
                else -> throw NotImplementedError("Can't handle algorithm ${privateKey.algorithm}")
            }
        }
    }

    override fun deriveSharedSecret(): ByteArray =
        deriveSharedSecret(provider, keyPair.private, otherPublicKey)
}