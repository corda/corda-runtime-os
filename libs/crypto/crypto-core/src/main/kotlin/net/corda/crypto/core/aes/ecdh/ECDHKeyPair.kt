package net.corda.crypto.core.aes.ecdh

import java.security.PublicKey

interface ECDHKeyPair {
    val publicKey: PublicKey

    fun encrypt(
        otherEphemeralPublicKey: PublicKey,
        params: AgreementParams,
        info: ByteArray,
        plainText: ByteArray,
        aad: ByteArray? = null
    ): ByteArray

    fun decrypt(
        otherEphemeralPublicKey: PublicKey,
        params: AgreementParams,
        info: ByteArray,
        cipherText: ByteArray,
        aad: ByteArray? = null
    ): ByteArray
}