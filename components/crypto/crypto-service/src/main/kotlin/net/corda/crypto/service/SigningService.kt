package net.corda.crypto.service

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceException
import java.security.KeyPair
import java.security.PublicKey

/**
 * The [SigningService] is an abstraction of the lower level key generation and signing.
 */
interface SigningService {
    companion object {
        val EMPTY_CONTEXT = emptyMap<String, String>()
    }

    /**
     * Return an instance of the [CipherSchemeMetadata] which is used by the current instance of [SigningService]
     */
    val schemeMetadata: CipherSchemeMetadata

    /**
     * Returns the list of schemes codes which are supported by the associated HSM integration.
     *
     * @param tenantId the tenant's id.
     * @param category the HSM's category.
     */
    fun getSupportedSchemes(tenantId: String, category: String): List<String>

    /**
     * Returns list of keys satisfying the filter condition. All filter values are combined as AND.
     *
     * @param skip the response paging information, number of records to skip.
     * @param take the response paging information, number of records to return, the actual number may be less than
     * requested.
     * @param orderBy the order by.
     * @param tenantId the tenant's id which the keys belong to.
     * @param filter the layered property map of the filter parameters such as
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
        orderBy: KeyOrderBy,
        filter: Map<String, String>
    ): Collection<SigningKeyInfo>

    /**
     * Returns list of keys for provided key ids.
     *
     * @param tenantId the tenant's id which the keys belong to.
     * @param ids The list of the key ids to look up for, the maximum number of items is 20.
     *
     * @throws IllegalArgumentException if the number of ids exceeds 20.
     */
    fun lookup(
        tenantId: String,
        ids: List<String>
    ): Collection<SigningKeyInfo>

    /**
     * Generates a new key to be used as a wrapping key. Some implementations may not have the notion of
     * the wrapping key in such cases the implementation should do nothing (note that requiresWrappingKey
     * in CryptoService should return false for such implementations).
     *
     * @param configId the HSM's configuration id which the key is generated in.
     * @param failIfExists a flag indicating whether the method should fail if a key already exists under
     * the provided alias or return normally without overriding the key.
     * @param context the optional key/value operation context.
     *
     * @throws [CryptoServiceBadRequestException] if a key already exists under this alias
     * and [failIfExists] is set to true.
     * @throws [CryptoServiceException] for general cryptographic exceptions.
     */
    fun createWrappingKey(
        configId: String,
        failIfExists: Boolean,
        masterKeyAlias: String,
        context: Map<String, String>
    )

    /**
     * Generates a new random key pair using the configured default key scheme and adds it to the internal key storage.
     *
     * @param tenantId the tenant's id which the key pair is generated for.
     * @param category The HSM category, such as TLS, LEDGER, etc.
     * @param alias the tenant defined key alias for the key pair to be generated.
     * @param scheme the key's scheme code name describing which type of the key to generate.
     * @param context the optional key/value operation context.
     *
     * The [tenantId] and [category] are used to find which HSM is being used to persist the actual key. After the key
     * is generated that information is stored alongside with other metadata so it wil be possible to find the HSM
     * storing the private key.
     *
     * @return The public part of the pair.
     */
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
     * @param tenantId the tenant's id which the key pair is generated for.
     * @param category The HSM category, such as TLS, LEDGER, etc.
     * @param alias the tenant defined key alias for the key pair to be generated.
     * @param externalId the external id to be associated with the key.
     * @param scheme the key's scheme code name describing which type of the key to generate.
     * @param context the optional key/value operation context.
     *
     * The [tenantId] and [category] are used to find which HSM is being used to persist the actual key. After the key
     * is generated that information is stored alongside with other metadata so it wil be possible to find the HSM
     * storing the private key.
     *
     * @return The public part of the pair.
     */
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
     * @param tenantId the tenant's id which the key pair is generated for.
     * @param category The HSM category, such as ACCOUNTS, CI, etc.
     * @param scheme the key's scheme code name describing which type of the key to generate.
     * @param context the optional key/value operation context.
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
     * @param tenantId the tenant's id which the key pair is generated for.
     * @param category The HSM category, such as ACCOUNTS, CI, etc.
     * @param externalId the external id to be associated with the key.
     * @param scheme the key's scheme code name describing which type of the key to generate.
     * @param context the optional key/value operation context.
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
     * Using the provided signing public key internally looks up the matching private key information and signs the data.
     * If the [PublicKey] is actually a [CompositeKey] the first leaf signing key hosted by the node is used.
     * Default signature scheme for the key scheme is used.
     *
     * @param tenantId the tenant's id which the key belongs to.
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
     * The [signatureSpec] is used to override the default signature scheme.
     *
     * @param tenantId the tenant's id which the key belongs to.
     */
    fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String> = EMPTY_CONTEXT
    ): DigitalSignature.WithKey
}
