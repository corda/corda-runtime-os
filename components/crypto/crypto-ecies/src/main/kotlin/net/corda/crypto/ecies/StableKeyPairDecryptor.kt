package net.corda.crypto.ecies

import net.corda.lifecycle.Lifecycle
import java.security.PublicKey

interface StableKeyPairDecryptor : Lifecycle {
    @Suppress("LongParameterList")
    fun decrypt(
        tenantId: String,
        salt: ByteArray,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        cipherText: ByteArray,
        aad: ByteArray?
    ): ByteArray = decrypt(
        tenantId = tenantId,
        salt = salt,
        publicKey = publicKey,
        otherPublicKey = otherPublicKey,
        cipherText = cipherText,
        aad = aad
    )
}