package net.corda.crypto.core

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoServiceExtensions
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.SharedSecretSpec
import net.corda.crypto.cipher.suite.SigningWrappedSpec
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey
import java.util.UUID

/**
 * Crypto service which can be used to sign and generate new key pairs.
 *
 * Corda always uses a single alias to identify a key pair. However, some HSMs need separate aliases for public
 * and private keys. In such cases, their names must be derived from the single key pair alias.
 * It could be suffixes or whatever internal naming convention is used.
 *
 * Also, it is not required to keep a public key in the HSM as that will be kept by the upstream Crypto Services.
 *
 * # Exception handling.
 *
 * As the service instances are decorated with decorators taking care of the timeout, throttling handling,
 * and exception normalisation the exceptions listed for each method below are as expected to be thrown by the decorators.
 *
 * The service should throw the most appropriate exception as it sees it, e.g.:
 * - [IllegalArgumentException] if the key is not found, the key scheme is not supported, the signature spec
 * is not supported or in general the input parameters are wrong
 * - if the internal state is wrong for an operation then the most appropriate exception would be [IllegalStateException]
 * - any other that is appropriate for the condition.
 *
 * If service encountered throttling situation and the downstream library doesn't handle that, it should throw
 * [net.corda.v5.crypto.exceptions.CryptoThrottlingException]
 *
 * The following exceptions are retried - [java.util.concurrent.TimeoutException],
 * [net.corda.v5.crypto.exceptions.CryptoException] (with the isRecoverable flag set to true),
 * some persistence exceptions like [javax.persistence.LockTimeoutException], [javax.persistence.QueryTimeoutException],
 * [javax.persistence.OptimisticLockException], [javax.persistence.PessimisticLockException] and some others.
 * Throw [java.util.concurrent.TimeoutException] only if the service should handle it before the upstream
 * library detects it.
 *
 * # About service extensions.
 *
 * The implementation of the [CryptoService] consists of the method of the implementation required and some optional
 * methods, which are called extensions. It's done this way, instead of breaking the functionality into several
 * interfaces, because the platform uses decorators to handle some common failure scenarios where using extension
 * interfaces would be awkward. If not stated explicitly in the description, the method is required.
 */
@Suppress("TooManyFunctions")
interface CryptoService {
    companion object {
        val EMPTY_CONTEXT = emptyMap<String, String>()
    }

    /**
     * List of crypto service extensions which are supported by this implementation of [CryptoService],
     * such as REQUIRE_WRAPPING_KEY, DELETE_KEYS.
     */
    val extensions: List<CryptoServiceExtensions>

    /**
     * List of key schemes and signature specs for each key which this implementation of [CryptoService] supports.
     */
    val supportedSchemes: Map<KeyScheme, List<SignatureSpec>>

    /**
     * Generates and optionally stores a key pair. The implementation is free to decide how the generated key
     * is stored - either in the corresponding HSM or wrapped and exported. The rule of thumb would be in the [spec]
     * has the [KeyGenerationSpec.alias] defined then it's expected that the key will be stored in the HSM otherwise
     * wrapped and exported but as mentioned above it's up to the concrete implementation. Such behaviour must be
     * defined beforehand and advertised. If the key is exported, its key material will be persisted
     * on the platform side.
     *
     * @param spec parameters to generate the key pair.
     * @param context the optional key/value operation context. The context will have at least two variables defined -
     * 'tenantId' and 'category'.
     *
     * @return Information about the generated key
     * depending on how the key is generated and persisted or wrapped and exported.
     *
     * @throws IllegalArgumentException the key scheme is not supported or in general the input parameters are wrong
     * @throws net.corda.v5.crypto.exceptions.CryptoException, non-recoverable
     */
    fun generateKeyPair(
        spec: KeyGenerationSpec,
        context: Map<String, String>
    ): GeneratedWrappedKey

    /**
     * Signs a byte array using the private key identified by the input arguments.
     *
     * @param spec [SigningWrappedSpec] to be used for signing.
     * @param data the data to be signed.
     * @param context the optional key/value operation context. The context will have at least one variable defined -
     * 'tenantId'.
     *
     * @return the signature bytes formatted according to the default signature spec.
     *
     * @throws IllegalArgumentException if the key is not found, the key scheme is not supported, the signature spec
     * is not supported or in general the input parameters are wrong
     * @throws net.corda.v5.crypto.exceptions.CryptoException, non-recoverable
     */
    fun sign(
        spec: SigningWrappedSpec,
        data: ByteArray,
        context: Map<String, String>,
    ): ByteArray

    /**
     * Generates a new key to be used as a wrapping key.
     *
     * @param wrappingKeyAlias the alias of the key to be used as a wrapping key.
     * @param failIfExists a flag indicating whether the method should fail if a key already exists under
     * the provided alias or return normally without overriding the key.
     * @param context the optional key/value operation context.
     *
     * @throws IllegalArgumentException if the [failIfExists] is set to true and the key exists
     * @throws UnsupportedOperationException if the operation is not supported
     * @throws net.corda.v5.crypto.exceptions.CryptoException, non-recoverable
     */
    fun createWrappingKey(
        wrappingKeyAlias: String,
        failIfExists: Boolean,
        context: Map<String, String>
    )

    /**
     * Deletes the key corresponding to the specified alias.
     * This method doesn't throw if the alias is not found, instead it has to return 'false'.
     *
     * @param alias the alias (as it stored in HSM) of the key being deleted.
     * @param context the optional key/value operation context. The context will have at least two variables defined -
     * 'tenantId' and 'keyType'.
     *
     * @return true if the key was deleted false otherwise
     *
     * @throws UnsupportedOperationException if the operation is not supported
     * @throws net.corda.v5.crypto.exceptions.CryptoException, non-recoverable
     */
    fun delete(alias: String, context: Map<String, String>): Boolean

    /**
     * Optional, derives Diffie–Hellman key agreement shared secret by using the private key associated
     * with [SharedSecretSpec.publicKey] and [SharedSecretSpec.otherPublicKey], note that the key schemes
     * of the [SharedSecretSpec.publicKey] and [SharedSecretSpec.otherPublicKey] must be the same and
     * the scheme must support the key agreement secret derivation.
     *
     * @param spec the operation parameters, [SharedSecretWrappedSpec]
     * @param context the optional key/value operation context. The context will have at least one variable defined -
     * 'tenantId'.
     *
     * @return the shared secret. Note that it's expected that the returned secret must be not post processed by
     * applying further transformations (e.g. HKDF or any other) as that will be done by Corda itself.
     *
     * @throws IllegalArgumentException if the key is not found, the key scheme is not supported, the signature spec
     * is not supported, the key schemes of the public keys are not matching or in general the input parameters are wrong
     * @throws UnsupportedOperationException if the operation is not supported
     * @throws net.corda.v5.crypto.exceptions.CryptoException, non-recoverable
     */
    fun deriveSharedSecret(
        spec: SharedSecretSpec,
        context: Map<String, String>,
    ): ByteArray


    /**
     * Return an instance of the [CipherSchemeMetadata] which is used by the current instance of [SigningService]
     */
    val schemeMetadata: CipherSchemeMetadata

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
    fun querySigningKeys(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: KeyOrderBy,
        filter: Map<String, String>,
    ): Collection<SigningKeyInfo>

    /**
     * Looks for keys by key ids.
     *
     * @param tenantId The tenant's id which the keys belong to.
     * @param keyIds Key ids to look keys for.
     *
     * @return the set of keys we could find information about, which may not be all keys. In particular
     *         information about keys for other tenants will be missing.
     */
    fun lookupSigningKeysByPublicKeyShortHash(
        tenantId: String,
        keyIds: List<ShortHash>,
    ): Collection<SigningKeyInfo>

    /**
     * Looks for keys by full key ids.
     *
     * @param tenantId The tenant's id which the keys belong to.
     * @param fullKeyIds Key ids to look keys for.
     *
     * @return the set of keys we could find information about, which may not be all keys. In particular
     *         information about keys for other tenants will be missing.
     */
    fun lookupSigningKeysByPublicKeyHashes(
        tenantId: String,
        fullKeyIds: List<SecureHash>,
    ): Collection<SigningKeyInfo>

    /**
     * Generates a new key to be used as a wrapping key. Some implementations may not have the notion of
     * the wrapping key in such cases the implementation should do nothing (note that requiresWrappingKey
     * in CryptoService should return false for such implementations).
     *
     * @param hsmId the HSM's id which the key is generated in.
     * @param failIfExists a flag indicating whether the method should fail if a key already exists under
     * the provided alias or return normally without overriding the key.
     * @param context the optional key/value operation context.
     */
    fun createWrappingKey(
        hsmId: String,
        failIfExists: Boolean,
        masterKeyAlias: String,
        context: Map<String, String>,
    )

    /**
     * Generates a new random key pair using the configured default key scheme and adds it to the internal key storage.
     *
     * @param tenantId the tenant's id which the key pair is generated for.
     * @param category The HSM category, such as TLS, LEDGER, etc.
     * @param alias the tenant defined key alias for the key pair to be generated, or null if not required.
     * @param externalId the external id to be associated with the key, or null if not required
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
        alias: String? = null,
        externalId: String? = null,
        scheme: KeyScheme,
        context: Map<String, String> = EMPTY_CONTEXT,
    ): GeneratedWrappedKey

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
        context: Map<String, String> = EMPTY_CONTEXT,
    ): DigitalSignatureWithKey

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
        context: Map<String, String> = EMPTY_CONTEXT,
    ): ByteArray

    /**
     * Rotate the encryption of a wrapping key, without changing the wrapping key itself,
     * just the way it is stored in the database.
     *
     * @param tenantId the tenant that holds the wrapping key
     * @param targetAlias the alias of the wrapping key that is to be decrypted then encrypted
     * @param newParentKeyAlias the new parent key to use for encrypting the wrapping key at rest
     * @return the new wrapping generation number
     *
     * @throws IllegalStateException if the newParentKeyAlias or the current parent key alias is not
     *         in the configuration unmanaged keys map or targetAlias is not found
     */
    fun rewrapWrappingKey(tenantId: String, targetAlias: String, newParentKeyAlias: String): Int

    /**
     * Encrypt [plainBytes] using the symmetric key associated with the given tenant.
     *
     * If the key is rotated, the ability to decrypt any data previously encrypted using that key will be lost.
     *
     * @param tenantId ID of the tenant owning the key. The tenant must have been assigned the HSM category
     * 'ENCRYPTION_SECRET'.
     * @param alias Optional. Alias of the symmetric key. If no alias is provided, the default
     *   alias for [tenantId] under HSM category 'ENCRYPTION_SECRET' will be used.
     * @param plainBytes The byte array to be encrypted.
     */
    fun encrypt(
        tenantId: String,
        plainBytes: ByteArray,
        alias: String? = null,
    ): ByteArray

    /**
     * Decrypt [cipherBytes] using the symmetric key associated with the given tenant.
     *
     * @param tenantId ID of the tenant owning the key. The tenant must have been assigned the HSM category
     * 'ENCRYPTION_SECRET'.
     * @param alias Optional. Alias of the symmetric key. If no alias is provided, the default alias for [tenantId]
     * under HSM category 'ENCRYPTION_SECRET' will be used.
     * @param cipherBytes The byte array to be decrypted.
     */
    fun decrypt(
        tenantId: String,
        cipherBytes: ByteArray,
        alias: String? = null,
    ): ByteArray

    /**
     * Rewrap all signing keys which are wrapped in the specified managed wrappingKey.
     *
     * @param managedWrappingKeyId The managed wrapping key which is being rotated away from
     * @param tenantId The tenant Id which uses the specified wrapping key
     *
     * @return The number of keys rewrapped
     */
    fun rewrapAllSigningKeysWrappedBy(managedWrappingKeyId: UUID, tenantId: String): Int
}
