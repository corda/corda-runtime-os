package net.corda.crypto.ecdh

import java.security.PublicKey

interface EphemeralKeyPairEncryptor {
    fun encrypt(
        digestName: String,
        salt: ByteArray,
        info: ByteArray,
        otherPublicKey: PublicKey,
        plainText: ByteArray,
        aad: ByteArray?
    ): EncryptedDataWithKey

    fun encrypt(
        salt: ByteArray,
        info: ByteArray,
        otherPublicKey: PublicKey,
        plainText: ByteArray,
        aad: ByteArray?
    ): EncryptedDataWithKey = encrypt(
        digestName = "SHA-256",
        salt = salt,
        info = info,
        otherPublicKey = otherPublicKey,
        plainText = plainText,
        aad = aad
    )
}