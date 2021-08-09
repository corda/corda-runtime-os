package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.schemes.SignatureScheme

/**
 * Holding class for the private key material. The interpretation what is stored in the key material
 * is up to the CryptoService implementing class - it could be the encrypted private key or its alias
 * in case if the HSM natively supports large number of keys.
 */
class WrappedPrivateKey(
    val keyMaterial: ByteArray,
    val masterKeyAlias: String,
    val signatureScheme: SignatureScheme,
    val encodingVersion: Int
)