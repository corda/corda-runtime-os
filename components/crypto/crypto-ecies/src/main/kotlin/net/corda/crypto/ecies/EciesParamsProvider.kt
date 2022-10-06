package net.corda.crypto.ecies

import java.security.PublicKey

/**
 * Supplies the salt and aad parameters for the shared secret derivation.
 */
fun interface EciesParamsProvider {
    fun get(ephemeralPublicKey: PublicKey, otherPublicKey: PublicKey): EciesParams

    class Default(
        val salt: ByteArray,
        val aad: ByteArray?
    ) : EciesParamsProvider {
        override fun get(ephemeralPublicKey: PublicKey, otherPublicKey: PublicKey): EciesParams = EciesParams(
            salt = salt,
            aad = aad
        )
    }
}