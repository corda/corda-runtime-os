package net.corda.p2p.linkmanager

import net.corda.p2p.test.KeyAlgorithm
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class KeyDeserialiser {

    private val rsaKeyFactory = KeyFactory.getInstance("RSA")
    private val ecdsaKeyFactory = KeyFactory.getInstance("EC")

    fun toPrivateKey(bytes: ByteArray, keyAlgorithm: KeyAlgorithm): PrivateKey {
        return when (keyAlgorithm) {
            KeyAlgorithm.ECDSA -> {
                ecdsaKeyFactory.generatePrivate(PKCS8EncodedKeySpec(bytes))
            }
            KeyAlgorithm.RSA -> {
                rsaKeyFactory.generatePrivate(PKCS8EncodedKeySpec(bytes))
            }
        }
    }

    fun toPublicKey(bytes: ByteArray, keyAlgorithm: KeyAlgorithm): PublicKey {
        return when (keyAlgorithm) {
            KeyAlgorithm.ECDSA -> {
                ecdsaKeyFactory.generatePublic(X509EncodedKeySpec(bytes))
            }
            KeyAlgorithm.RSA -> {
                rsaKeyFactory.generatePublic(X509EncodedKeySpec(bytes))
            }
        }
    }
}