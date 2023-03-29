package net.corda.crypto.client

import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.InvalidParamsException
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.crypto.core.ShortHash
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.lifecycle.Lifecycle
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import java.security.KeyPair
import java.security.PublicKey

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
     * Filters the input [PublicKey]s down to a collection of keys that this tenant owns (has private keys for).
     *
     * Please note that if used with [usingFullIds] off it will filter keys by looking into short key ids of [candidateKeys].
     * Using short key ids means that a key clash on short key id is more likely to happen. Effectively
     * it means that a requested key not owned by the tenant, however having same short key id (clashing short key id)
     * with a different key which is owned by the tenant, will lead to return the owned key while it should not.
     * This api is added for convenience of using short hashes in cases like for e.g. REST interfaces. For more critical
     * key operations where we need to avoid key id clashes [lookupKeysByFullIds] can be used.
     *
     * @param tenantId The tenant owning the key.
     * @param candidateKeys The [PublicKey]s to filter.
     *
     * @return A collection of [PublicKey]s that this node owns.
     */
    fun filterMyKeys(
        tenantId: String,
        candidateKeys: Collection<PublicKey>,
        usingFullIds: Boolean = false
    ): Collection<PublicKey>

    /**
     * Generates a new random key pair using the configured default key scheme and adds it to the internal key storage.
     *
     * @param tenantId The tenant owning the key.
     * @param category The key category, such as TLS, LEDGER, etc.
     * @param alias the tenant defined key alias for the key pair to be generated.
     * @param scheme the key's scheme code name describing which type of the key to generate.
     * @param context the optional key/value operation context.
     * @throws [KeyAlreadyExistsException] if a key with the provided alias already exists for the tenant.
     * @throws [InvalidParamsException] If the tenant is not configured for the given category or if the key's scheme is invalid.
     *
     * @return The public part of the pair.
     */
    @Throws(KeyAlreadyExistsException::class, InvalidParamsException::class)
    fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        scheme: String,
        context: Map<String, String> = EMPTY_CONTEXT
    ): PublicKey

    /**
     * Generates a new random key pair using the configured default key scheme and adds it to the internal key storage.
     *
     * @param tenantId The tenant owning the key.
     * @param category The key category, such as TLS, LEDGER, etc.
     * @param alias the tenant defined key alias for the key pair to be generated.
     * @param externalId an id associated with the key, the service doesn't use any semantic beyond association.
     * @param scheme the key's scheme code name describing which type of the key to generate.
     * @param context the optional key/value operation context.
     * @throws [KeyAlreadyExistsException] if a key with the provided alias already exists for the tenant.
     * @throws [InvalidParamsException] If the tenant is not configured for the given category or if the key's scheme is invalid.
     *
     * @return The public part of the pair.
     */
    @Throws(KeyAlreadyExistsException::class, InvalidParamsException::class)
    @Suppress("LongParameterList")
    fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        externalId: String,
        scheme: String,
        context: Map<String, String> = EMPTY_CONTEXT
    ): PublicKey

    /**
     * Generates a new random [KeyPair] and adds it to the internal key storage.
     *
     * @param tenantId The tenant owning the key.
     * @param category The key category, such as ACCOUNTS, CI, etc.
     * @param scheme the key's scheme code name describing which type of the key to generate.
     * @param context the optional key/value operation context.
     * @throws [InvalidParamsException] If the key's scheme is invalid.
     *
     * @return The [PublicKey] of the generated [KeyPair].
     */
    fun freshKey(
        tenantId: String,
        category: String,
        scheme: String,
        context: Map<String, String> = EMPTY_CONTEXT
    ): PublicKey

    /**
     * Generates a new random [KeyPair] and adds it to the internal key storage. Associates the public key to
     * an external id.
     *
     * @param tenantId The tenant owning the key.
     * @param category The key category, such as ACCOUNTS, CI, etc.
     * @param externalId an id associated with the key, the service doesn't use any semantic beyond association.
     * @param scheme the key's scheme code name describing which type of the key to generate.
     * @param context the optional key/value operation context.
     * @throws [InvalidParamsException] If the key's scheme is invalid.
     *
     * @return The [PublicKey] of the generated [KeyPair].
     */
    fun freshKey(
        tenantId: String,
        category: String,
        externalId: String,
        scheme: String,
        context: Map<String, String> = EMPTY_CONTEXT
    ): PublicKey

    /**
     * Using the provided signing public key internally looks up the matching private key and signs the data.
     * If the [PublicKey] is actually a [CompositeKey] the first leaf signing key hosted by the node is used.
     */
    fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String> = EMPTY_CONTEXT
    ): DigitalSignatureWithKey

    /**
     * Using the provided signing public key internally looks up the matching private key and signs the data.
     * If the [PublicKey] is actually a [CompositeKey] the first leaf signing key hosted by the node is used.
     * The [digest] together with [publicKey] is used to infer the [SignatureSpec].
     *
     * @throws IllegalArgumentException if the [SignatureSpec] cannot be inferred from the parameters -
     * e.g. EdDSA supports only 'NONEwithEdDSA' signatures so if the SHA-256 will be passed as the parameter
     * that will result in the exception.
     */
    fun sign(
        tenantId: String,
        publicKey: PublicKey,
        digest: DigestAlgorithmName,
        data: ByteArray,
        context: Map<String, String> = EMPTY_CONTEXT
    ): DigitalSignatureWithKey

    /**
     * Returns list of keys satisfying the filter condition. All filter values are combined as AND.
     *
     * @param skip the response paging information, number of records to skip.
     * @param take the response paging information, number of records to return, the actual number may be less than
     * requested.
     * @param orderBy the order by.
     * @param tenantId the tenant's id which the keys belong to.
     * @param filter the optional layered property map of the filter parameters such as
     * category (the HSM's category which handles the keys),
     * schemeCodeName (the key's signature scheme name),
     * alias (the alias which is assigned by the tenant),
     * masterKeyAlias (the wrapping key alias),
     * externalId (an id associated with the key),
     * createdAfter (specifies inclusive time after which a key was created),
     * createdBefore (specifies inclusive time before which a key was created).
     */
    fun lookup(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: CryptoKeyOrderBy,
        filter: Map<String, String>
    ): List<CryptoSigningKey>

    /**
     * Returns keys with key ids in [keyIds] which are owned by tenant of id [tenantId].
     *
     * Please note that this method uses short key ids which means that a key clash on short key id is more likely to happen.
     * Effectively it means that a requested key not owned by the tenant, however having same short key id (clashing short key id)
     * with a different key which is owned by the tenant, will lead to return the owned key while it should not.
     * This api is added for convenience of using short hashes in cases like for e.g. REST interfaces. For more critical
     * key operations where we need to avoid key id clashes [lookupKeysByFullIds] can be used.
     *
     * @throws IllegalArgumentException if the number of ids exceeds 20.
     */
    fun lookupKeysByIds(tenantId: String, keyIds: List<ShortHash>): List<CryptoSigningKey>

    /**
     * Returns keys with full key ids in [fullKeyIds] which are owned by tenant of id [tenantId].
     *
     * @throws IllegalArgumentException if the number of ids exceeds 20.
     */
    fun lookupKeysByFullIds(tenantId: String, fullKeyIds: List<SecureHash>): List<CryptoSigningKey>

    /**
     * Generates a new key to be used as a wrapping key. Some implementations may not have the notion of
     * the wrapping key in such cases the implementation should do nothing .
     *
     * @param hsmId the HSM id which the key is generated in.
     * @param failIfExists a flag indicating whether the method should fail if a key already exists under
     * the provided alias or return normally without overriding the key.
     * @param context the optional key/value operation context.
     *
     * @throws IllegalArgumentException if a key already exists under this alias
     * and [failIfExists] is set to true.
     * @throws net.corda.v5.crypto.exceptions.CryptoException for general cryptographic exceptions.
     */
    fun createWrappingKey(
        hsmId: String,
        failIfExists: Boolean,
        masterKeyAlias: String,
        context: Map<String, String>
    )

    /**
     * Derive Diffie–Hellman key agreement shared secret by using the private key associated with [publicKey]
     * and [otherPublicKey], note that the key schemes of the [publicKey] and [otherPublicKey] must be the same and
     * the scheme must support the key agreement secret derivation.
     *
     * @param tenantId the tenant which owns the key pair referenced by the [publicKey]
     * @param publicKey the public key of the key pair
     * @param otherPublicKey the public of the "other" party which should be used to derive the secret
     * @param context the optional key/value operation context.
     *
     * @return the shared secret.
     */
    fun deriveSharedSecret(
        tenantId: String,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        context: Map<String, String> = EMPTY_CONTEXT
    ): ByteArray
}