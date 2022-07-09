package net.corda.crypto.ecdh

import java.security.PublicKey

interface ECDHKeyPair {
    val publicKey: PublicKey
    val otherPublicKey: PublicKey
    val digestName: String
    fun encrypt(
        salt: ByteArray,
        info: ByteArray,
        plainText: ByteArray,
        aad: ByteArray? = null
    ): ByteArray

    fun decrypt(
        salt: ByteArray,
        info: ByteArray,
        cipherText: ByteArray,
        aad: ByteArray? = null
    ): ByteArray
}