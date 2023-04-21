package net.corda.crypto.hes

import java.security.PublicKey

/**
 * Supplies the salt and aad parameters for the shared secret derivation.
 */
fun interface HybridEncryptionParamsProvider {
    fun get(ephemeralPublicKey: PublicKey, otherPublicKey: PublicKey): HybridEncryptionParams

    class Default(
        val salt: ByteArray,
        val aad: ByteArray?
    ) : HybridEncryptionParamsProvider {
        override fun get(ephemeralPublicKey: PublicKey, otherPublicKey: PublicKey): HybridEncryptionParams =
            HybridEncryptionParams(
                salt = salt,
                aad = aad
            )
    }
}