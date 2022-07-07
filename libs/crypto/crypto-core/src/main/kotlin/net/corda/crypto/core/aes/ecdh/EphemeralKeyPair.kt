package net.corda.crypto.core.aes.ecdh

import net.corda.crypto.core.Encryptor
import java.security.PublicKey

interface EphemeralKeyPair {
    val publicKey: PublicKey
    fun deriveSharedEncryptor(
        otherEphemeralPublicKey: PublicKey,
        params: ECDHAgreementParams,
        info: ByteArray
    ): Encryptor
}