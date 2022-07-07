package net.corda.crypto.core.aes.ecdh

import java.security.PublicKey

interface EphemeralKeyPair {
    val publicKey: PublicKey
    fun deriveSharedEncryptor(
        otherEphemeralPublicKey: PublicKey,
        params: ECDHAgreementParams,
        info: ByteArray
    ): AesGcmEncryptor
}