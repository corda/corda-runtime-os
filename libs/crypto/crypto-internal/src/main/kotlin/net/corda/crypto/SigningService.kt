package net.corda.crypto

import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import java.security.KeyPair
import java.security.PublicKey
import java.util.UUID

/**
 * The [SigningService] is an abstraction of the lower level key generation and signing.
 */
interface SigningService {
    companion object {
        val EMPTY_CONTEXT = emptyMap<String, String>()
    }

    /**
     * Tenant id (member or cluster) which the instance was created for.
     */
    val tenantId: String

    /**
     * Returns the public key for the given alias.
     */
    fun findPublicKey(alias: String): PublicKey?

    /**
     * Filters the input [PublicKey]s down to a collection of keys that this tenant owns (has private keys for).
     *
     * @param candidateKeys The [PublicKey]s to filter.
     *
     * @return A collection of [PublicKey]s that this node owns.
     */
    fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey>

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
     * Using the provided signing public key internally looks up the matching private key information and signs the data.
     * If the [PublicKey] is actually a [CompositeKey] the first leaf signing key hosted by the node is used.
     * Default signature scheme for the key scheme is used.
     */
    fun sign(
        publicKey: PublicKey,
        data: ByteArray,
        context: Map<String, String> = EMPTY_CONTEXT
    ): DigitalSignature.WithKey

    /**
     * Using the provided signing public key internally looks up the matching private key and signs the data.
     * If the [PublicKey] is actually a [CompositeKey] the first leaf signing key hosted by the node is used.
     * The [signatureSpec] is used to override the default signature scheme
     */
    fun sign(
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String> = EMPTY_CONTEXT
    ): DigitalSignature.WithKey

    /**
     * Sign a byte array using the private key identified by the input alias.
     * Returns the signature bytes formatted according to the signature scheme (signAlgorithm).
     * Default signature scheme for the key scheme is used.
     */
    fun sign(alias: String, data: ByteArray, context: Map<String, String> = EMPTY_CONTEXT): ByteArray

    /**
     * Sign a byte array using the private key identified by the input alias.
     * Returns the signature bytes formatted according to the signature scheme (signAlgorithm).
     * The [signatureSpec] is used to override the default signature scheme
     */
    fun sign(
        alias: String,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String> = EMPTY_CONTEXT
    ): ByteArray
}
