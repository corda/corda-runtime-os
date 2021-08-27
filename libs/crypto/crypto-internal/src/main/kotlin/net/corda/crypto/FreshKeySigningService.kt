package net.corda.crypto

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.cipher.suite.schemes.SignatureSpec
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.exceptions.CryptoServiceException
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*

/**
 * The [FreshKeySigningService] provides operation support for KeyManagementService and is responsible for storing
 * and using private keys to sign things. An implementation of this may, for example,
 * call out to a hardware security module that enforces various auditing and frequency-of-use requirements.
 */
interface FreshKeySigningService {
    /**
     * Generates a new random [KeyPair] and adds it to the internal key storage.
     *
     * @return The [PublicKey] of the generated [KeyPair].
     */
    @Suspendable
    fun freshKey(): PublicKey

    /**
     * Generates a new random [KeyPair] and adds it to the internal key storage. Associates the public key to an external Id.
     *
     * @return The [PublicKey] of the generated [KeyPair].
     */
    @Suspendable
    fun freshKey(externalId: UUID): PublicKey

    /**
     * Using the provided signing [PublicKey], internally looks up the matching [PrivateKey] and signs the data.
     *
     * @param data The data to sign over using the chosen key.
     *
     * @param publicKey The [PublicKey] partner to an internally held [PrivateKey], either derived from the node's primary identity, or
     * previously generated via the [freshKey] method. If the [PublicKey] is actually a [CompositeKey], the first leaf signing key hosted by
     * the node is used.
     *
     * @return A [DigitalSignature.WithKey] representing the signed data and the [PublicKey] that belongs to the same [KeyPair] as the
     * [PrivateKey] that signed the data.
     *
     * Default signature scheme for the key scheme is used.
     */
    @Suspendable
    fun sign(publicKey: PublicKey, data: ByteArray): DigitalSignature.WithKey

    /**
     * Using the provided signing [PublicKey], internally looks up the matching [PrivateKey] and signs the data.
     *
     * @param data The data to sign over using the chosen key.
     *
     * @param publicKey The [PublicKey] partner to an internally held [PrivateKey], either derived from the node's primary identity, or
     * previously generated via the [freshKey] method. If the [PublicKey] is actually a [CompositeKey], the first leaf signing key hosted by
     * the node is used.
     *
     * @return A [DigitalSignature.WithKey] representing the signed data and the [PublicKey] that belongs to the same [KeyPair] as the
     * [PrivateKey] that signed the data.
     *
     * @throws IllegalArgumentException If the input key is not a member of [keys].
     */
    @Suspendable
    fun sign(publicKey: PublicKey, signatureSpec: SignatureSpec, data: ByteArray): DigitalSignature.WithKey

    /**
     * Ensures that the wrapping key is generated if required by the underlying [CryptoService] implementation.
     * The alias under which the created wrapping key is created would have to be provided in the configuration.
     * The method would be called only during the bootstrapping process.
     *
     * @throws [CryptoServiceException] for general cryptographic exceptions.
     */
    fun ensureWrappingKey()
}
