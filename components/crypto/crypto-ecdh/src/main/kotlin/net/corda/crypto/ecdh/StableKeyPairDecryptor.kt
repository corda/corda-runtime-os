package net.corda.crypto.ecdh

import net.corda.lifecycle.Lifecycle
import java.security.PublicKey

interface StableKeyPairDecryptor : Lifecycle {
    fun decrypt(
        tenantId: String,
        digestName: String,
        salt: ByteArray,
        info: ByteArray,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        cipherText: ByteArray,
        aad: ByteArray?
    ): ByteArray

    fun decrypt(
        tenantId: String,
        salt: ByteArray,
        info: ByteArray,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        cipherText: ByteArray,
        aad: ByteArray?
    ): ByteArray = decrypt(
        tenantId = tenantId,
        digestName = "SHA-256",
        salt = salt,
        info = info,
        publicKey = publicKey,
        otherPublicKey = otherPublicKey,
        cipherText = cipherText,
        aad = aad
    )
}