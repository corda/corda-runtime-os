package net.corda.crypto

import net.corda.v5.crypto.SignatureSpec
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
    companion object {
        val EMPTY_CONTEXT = emptyMap<String, String>()
    }

    /**
     * Generates a new random [KeyPair] and adds it to the internal key storage.
     *
     * @param context the optional key/value operation context.
     *
     * @return The [PublicKey] of the generated [KeyPair].
     */
    fun freshKey(context: Map<String, String> = EMPTY_CONTEXT): PublicKey

    /**
     * Generates a new random [KeyPair] and adds it to the internal key storage. Associates the public key to
     * an external id.
     *
     * @param context the optional key/value operation context.
     *
     * @return The [PublicKey] of the generated [KeyPair].
     */
    fun freshKey(externalId: UUID, context: Map<String, String> = EMPTY_CONTEXT): PublicKey

    /**
     * Using the provided signing [PublicKey], internally looks up the matching [PrivateKey] and signs the data.
     *
     * @param data The data to sign over using the chosen key.
     *
     * @param publicKey The [PublicKey] partner to an internally held [PrivateKey], either derived from
     * the node's primary identity, or previously generated via the [freshKey] method. If the [PublicKey] is actually
     * a [CompositeKey], the first leaf signing key hosted by the node is used.
     *
     * @param context the optional key/value operation context.
     *
     * @return A [DigitalSignature.WithKey] representing the signed data and the [PublicKey] that belongs to
     * the same [KeyPair] as the [PrivateKey] that signed the data.
     *
     * Default signature scheme for the key scheme is used.
     */
    fun sign(
        publicKey: PublicKey,
        data: ByteArray,
        context: Map<String, String> = EMPTY_CONTEXT
    ): DigitalSignature.WithKey

    /**
     * Using the provided signing [PublicKey], internally looks up the matching [PrivateKey] and signs the data.
     *
     * @param data The data to sign over using the chosen key.
     *
     * @param publicKey The [PublicKey] partner to an internally held [PrivateKey], either derived from
     * the node's primary identity, or previously generated via the [freshKey] method. If the [PublicKey] is actually
     * a [CompositeKey], the first leaf signing key hosted by the node is used.
     *
     * @param context the optional key/value operation context.
     *
     * @return A [DigitalSignature.WithKey] representing the signed data and the [PublicKey] that belongs to
     * the same [KeyPair] as the [PrivateKey] that signed the data.
     *
     * @throws IllegalArgumentException If the input key is not a member of [keys].
     */
    fun sign(
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String> = EMPTY_CONTEXT
    ): DigitalSignature.WithKey

    /**
     * Ensures that the wrapping key is generated if required by the underlying [CryptoService] implementation.
     * The alias under which the created wrapping key is created would have to be provided in the configuration.
     * The method would be called only during the bootstrapping process.
     *
     * @throws [CryptoServiceException] for general cryptographic exceptions.
     */
    fun ensureWrappingKey()

    /**
     * Filters the input [PublicKey]s down to a collection of keys that this node owns (has private keys for).
     *
     * @param candidateKeys The [PublicKey]s to filter.
     *
     * @return A collection of [PublicKey]s that this node owns.
     */
    fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey>
}
