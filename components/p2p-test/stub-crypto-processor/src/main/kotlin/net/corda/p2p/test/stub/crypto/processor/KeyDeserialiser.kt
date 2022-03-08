package net.corda.p2p.test.stub.crypto.processor

import net.corda.p2p.test.KeyAlgorithm
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class KeyDeserialiser(
    keyFactory: ((String) -> KeyFactory) = { KeyFactory.getInstance(it) }
) {

    private val rsaKeyFactory = keyFactory("RSA")
    private val ecdsaKeyFactory = keyFactory("EC")

    private fun KeyAlgorithm.getFactory(): KeyFactory = when (this) {
        KeyAlgorithm.ECDSA -> {
            ecdsaKeyFactory
        }
        KeyAlgorithm.RSA -> {
            rsaKeyFactory
        }
    }

    @Synchronized
    fun toPrivateKey(bytes: ByteArray, keyAlgorithm: KeyAlgorithm): PrivateKey {
        return keyAlgorithm.getFactory().generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    @Synchronized
    fun toPublicKey(bytes: ByteArray, keyAlgorithm: KeyAlgorithm): PublicKey {
        return keyAlgorithm.getFactory().generatePublic(X509EncodedKeySpec(bytes))
    }
}
