package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.schemes.SignatureScheme

/**
 * Holding class for the private key material.
 *
 * @property tenantId The tenant id which the key pair belongs to.
 * @property keyMaterial The encoded and encrypted private key.
 * @property masterKeyAlias The wrapping key's alias which was used for wrapping, the value
 * could be null for HSMs which use built-in wrapping keys.
 * @property encodingVersion The encoding version which was used to encode the private key.
 * @property signatureScheme The scheme for the signing operation.
 */
class SigningWrappedSpec(
    override val tenantId: String,
    val keyMaterial: ByteArray,
    val masterKeyAlias: String?,
    val encodingVersion: Int,
    override val signatureScheme: SignatureScheme
) : SigningSpec