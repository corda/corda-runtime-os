package net.corda.crypto.client

import net.corda.data.crypto.config.HSMInfo
import net.corda.data.crypto.wire.ops.rpc.HSMKeyDetails
import net.corda.lifecycle.Lifecycle
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import java.security.KeyPair
import java.security.PublicKey
import java.util.UUID

/**
 * The crypto operations client to generate fresh keys, sign, find or filter public keys, some HSM related queries.
 */
interface CryptoOpsClient : Lifecycle {
    companion object {
        val EMPTY_CONTEXT = emptyMap<String, String>()
    }

    /**
     * Returns the list of schemes codes which are supported by the associated HSM integration.
     */
    fun getSupportedSchemes(tenantId: String, category: String): List<String>

    /**
     * Returns the public key for the given alias.
     *
     * @param tenantId The tenant owning the key.
     * @param alias The key alias assigned by tenant.
     *
     * @return The [PublicKey] if of the pair if it's found, otherwise null.
     */
    fun findPublicKey(tenantId: String, alias: String): PublicKey?

    /**
     * Filters the input [PublicKey]s down to a collection of keys that this tenant owns (has private keys for).
     *
     * @param tenantId The tenant owning the key.
     * @param candidateKeys The [PublicKey]s to filter.
     *
     * @return A collection of [PublicKey]s that this node owns.
     */
    fun filterMyKeys(tenantId: String, candidateKeys: Iterable<PublicKey>): Iterable<PublicKey>

    /**
     * Generates a new random key pair using the configured default key scheme and adds it to the internal key storage.
     *
     * @param tenantId The tenant owning the key.
     * @param category The key category, such as TLS, LEDGER, etc. Don't use FRESH_KEY category as there is separate API
     * for the fresh keys which is base around wrapped keys.
     * @param alias the tenant defined key alias for the key pair to be generated.
     * @param context the optional key/value operation context.
     *
     * @return The public part of the pair.
     */
    fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        context: Map<String, String> = EMPTY_CONTEXT
    ): PublicKey

    /**
     * Generates a new random [KeyPair] and adds it to the internal key storage.
     *
     * @param tenantId The tenant owning the key.
     * @param context the optional key/value operation context.
     *
     * @return The [PublicKey] of the generated [KeyPair].
     */
    fun freshKey(tenantId: String, context: Map<String, String> = EMPTY_CONTEXT): PublicKey

    /**
     * Generates a new random [KeyPair] and adds it to the internal key storage. Associates the public key to
     * an external id.
     *
     * @param tenantId The tenant owning the key.
     * @param externalId Some id associated with the key, the service doesn't use any semantic beyond association.
     * @param context the optional key/value operation context.
     *
     * @return The [PublicKey] of the generated [KeyPair].
     */
    fun freshKey(
        tenantId: String,
        externalId: UUID,
        context: Map<String, String> = EMPTY_CONTEXT
    ): PublicKey

    /**
     * Using the provided signing public key internally looks up the matching private key information and signs the data.
     * If the [PublicKey] is actually a [CompositeKey] the first leaf signing key hosted by the node is used.
     * Default signature scheme for the key scheme is used.
     */
    fun sign(
        tenantId: String,
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
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String> = EMPTY_CONTEXT
    ): DigitalSignature.WithKey

    /**
     * Sign a byte array using the private key identified by the input alias.
     * Returns the signature bytes formatted according to the signature scheme (signAlgorithm).
     * Default signature scheme for the key scheme is used.
     * Note that the alias is scoped to tenant, so it would be enough for the system to figure out which HSM to use
     * without having category as parameter.
     */
    fun sign(
        tenantId: String,
        alias: String,
        data: ByteArray,
        context: Map<String, String> = EMPTY_CONTEXT
    ): ByteArray

    /**
     * Sign a byte array using the private key identified by the input alias.
     * Returns the signature bytes formatted according to the signature scheme (signAlgorithm).
     * The [signatureSpec] is used to override the default signature scheme
     * Note that the alias is scoped to tenant, so it would be enough for the system to figure out which HSM to use
     * without having category as parameter.
     */
    fun sign(
        tenantId: String,
        alias: String,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String> = EMPTY_CONTEXT
    ): ByteArray

    /**
     * Looks up key details by its alias.
     * Note that the alias is scoped to tenant, so it would be enough for the system to figure out which HSM to use
     * without having category as parameter.
     *
     * @return The key details if it's found otherwise null.
     */
    fun findHSMKey(tenantId: String, alias: String): HSMKeyDetails?

    /**
     * Looks up key details by its public key.
     * Note that the alias is scoped to tenant, so it would be enough for the system to figure out which HSM to use
     * without having category as parameter.
     *
     * @return The key details if it's found otherwise null.
     */
    fun findHSMKey(tenantId: String, publicKey: PublicKey): HSMKeyDetails?

    /**
     * Looks up information about the assigned HSM.
     *
     * @return The HSM's info if it's assigned otherwise null.
     */
    fun findHSM(tenantId: String, category: String) : HSMInfo?
}