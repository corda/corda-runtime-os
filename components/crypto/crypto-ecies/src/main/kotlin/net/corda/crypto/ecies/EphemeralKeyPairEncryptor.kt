package net.corda.crypto.ecies

import java.security.PublicKey

interface EphemeralKeyPairEncryptor {
    fun encrypt(
        salt: ByteArray,
        otherPublicKey: PublicKey,
        plainText: ByteArray,
        aad: ByteArray?
    ): EncryptedDataWithKey = encrypt(
        salt = salt,
        otherPublicKey = otherPublicKey,
        plainText = plainText,
        aad = aad
    )
}