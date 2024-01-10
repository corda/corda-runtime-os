package net.corda.crypto.softhsm.impl

import com.github.benmanes.caffeine.cache.Cache
import net.corda.crypto.cipher.suite.CRYPTO_CATEGORY
import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoServiceExtensions
import net.corda.crypto.cipher.suite.CustomSignatureSpec
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.cipher.suite.SharedSecretSpec
import net.corda.crypto.cipher.suite.SharedSecretWrappedSpec
import net.corda.crypto.cipher.suite.SigningWrappedSpec
import net.corda.crypto.cipher.suite.getParamsSafely
import net.corda.crypto.cipher.suite.publicKeyId
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.KeySchemeCapability
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.InvalidParamsException
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.crypto.core.KeyOrderBy
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.core.isRecoverable
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.hes.core.impl.deriveDHSharedSecret
import net.corda.crypto.impl.SignatureInstances
import net.corda.crypto.impl.getSigningData
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.SigningRepositoryFactory
import net.corda.crypto.softhsm.TenantInfoService
import net.corda.crypto.softhsm.WrappingRepositoryFactory
import net.corda.crypto.softhsm.deriveSupportedSchemes
import net.corda.metrics.CordaMetrics
import net.corda.utilities.debug
import net.corda.utilities.trace
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoException
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.time.Duration
import javax.crypto.Cipher

const val WRAPPING_KEY_ENCODING_VERSION: Int = 1
const val PRIVATE_KEY_ENCODING_VERSION: Int = 1

data class ShortHashCacheKey(val tenantId: String, val publicKeyId: ShortHash)

data class OwnedKeyRecord(val publicKey: PublicKey, val data: SigningKeyInfo)

/**
 * This class is all about the business logic of generating, storing and using key pairs; it can be run
 * without a database, without OSGi and without SmartConfig, which makes it easy to test.
 *
 * @param wrappingRepositoryFactory which provides a factory for [WrappingRepository], which provides save and
 *        find operations for wrapping keys on a specific tenant.
 * @param signingRepositoryFactory which provides a factory for [SigningRepository], which provides save and
 *        find operations for signing keys for a specific tenant.
 * @param schemeMetadata which specifies encryption schemes, digests schemes and a source of randomness
 * @param defaultUnmanagedWrappingKeyName The unmanaged wrapping key that will be used by default for new wrapping keys
 * @param digestService supply a platform digest service instance; if not one will be constructed
 * @param shortHashCache an optional [Cache] which optimises access lookup by short ID hash, or null for no caching
 * @param wrappingKeyCache an optional [Cache] which optimises access to wrapping keys, or null for no caching
 * @param privateKeyCache an optional [Cache] which optimises access to private keys, or null for no caching
 * @param keyPairGeneratorFactory creates a key pair generator given algorithm and provider. For instance:
 *     `{ algorithm: String, provider: Provider -> KeyPairGenerator.getInstance(algorithm, provider) }`
 * @param wrappingKeyFactory creates a wrapping key given scheme metadata. For instance:
 *        `{ WrappingKeyImpl.generateWrappingKey(it) }`
 */

@Suppress("LongParameterList", "TooManyFunctions")
open class SoftCryptoService(
    private val wrappingRepositoryFactory: WrappingRepositoryFactory,
    private val signingRepositoryFactory: SigningRepositoryFactory,
    override val schemeMetadata: CipherSchemeMetadata,
    private val defaultUnmanagedWrappingKeyName: String,
    private val unmanagedWrappingKeys: Map<String, WrappingKey>,
    private val digestService: PlatformDigestService,
    private val wrappingKeyCache: Cache<String, WrappingKey>?,
    private val shortHashCache: Cache<ShortHashCacheKey, SigningKeyInfo>,
    private val privateKeyCache: Cache<PublicKey, PrivateKey>?,
    private val keyPairGeneratorFactory: (algorithm: String, provider: Provider) -> KeyPairGenerator,
    private val wrappingKeyFactory: (schemeMetadata: CipherSchemeMetadata) -> WrappingKey = {
        WrappingKeyImpl.generateWrappingKey(it)
    },
    private val tenantInfoService: TenantInfoService,
) : Closeable, CryptoService {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val SIGN_OPERATION_NAME = "sign"
    }

    private val signatureInstances = SignatureInstances(schemeMetadata.providers)

    override val supportedSchemes = deriveSupportedSchemes(schemeMetadata)

    override val extensions = listOf(CryptoServiceExtensions.REQUIRE_WRAPPING_KEY)

    /**
     * Run a block of code, and wrap certain exceptions in CryptoException with a message
     * that includes a description of what was being done. The set of exceptions that are to
     * be wrapped is controlled by the `isRecoverable` extensions function. The idea is that
     * we let the callers know when it is worth trying again.
     * 
     * @param description A message describing what the block will do
     * @param block A callback to be executed
     * 
     * @return The result of the callback.
     */
    private fun <R> recoverable(description: String, block: () -> R) = try {
        block()
    } catch (e: RuntimeException) {
        if (!e.isRecoverable())
            throw e
        else
            throw CryptoException("Calling $description failed in a potentially recoverable way", e)
    }

    override fun createWrappingKey(wrappingKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) {
        require(wrappingKeyAlias != "") { "Alias must not be empty" }
        val isCached = wrappingKeyCache?.getIfPresent(wrappingKeyAlias) != null
        val tenantId = computeTenantId(context)
        val isAvailable = if (isCached) true else recoverable("createWrappingKey findKey") {
            wrappingRepositoryFactory.create(tenantId).use {
                it.findKey(wrappingKeyAlias) != null
            }
        }
        logger.trace {
            "createWrappingKey(alias=$wrappingKeyAlias failIfExists=$failIfExists) cached=$isCached available=$isAvailable"
        }
        if (isAvailable) {
            if (failIfExists) throw IllegalStateException("There is an existing key with the alias: $wrappingKeyAlias")
            logger.debug { "Not creating wrapping key for '$wrappingKeyAlias' since a key is available" }
            return
        }
        val wrappingKey = recoverable("createWrappingKey generate wrapping key") { wrappingKeyFactory(schemeMetadata) }
        val parentKeyName = context.get("wrappingKey") ?: defaultUnmanagedWrappingKeyName
        val parentKey = unmanagedWrappingKeys.get(parentKeyName)
        if (parentKey == null) {
            throw IllegalStateException("No wrapping key $parentKeyName found")
        }
        val wrappingKeyEncrypted = recoverable("wrap") { parentKey.wrap(wrappingKey) }
        val wrappingKeyInfo =
            WrappingKeyInfo(
                WRAPPING_KEY_ENCODING_VERSION,
                wrappingKey.algorithm,
                wrappingKeyEncrypted,
                1,
                parentKeyName
            )
        recoverable("createWrappingKey save key") {
            wrappingRepositoryFactory.create(tenantId).use {
                it.saveKey(wrappingKeyAlias, wrappingKeyInfo)
            }
        }
        logger.trace("Stored wrapping key alias $wrappingKeyAlias context ${context.toString()}")
        wrappingKeyCache?.put(wrappingKeyAlias, wrappingKey)
    }

    override fun delete(alias: String, context: Map<String, String>): Boolean =
        throw UnsupportedOperationException("The service does not support key deletion.")

    override fun deriveSharedSecret(
        spec: SharedSecretSpec,
        context: Map<String, String>,
    ): ByteArray {
        require(spec is SharedSecretWrappedSpec) {
            "The service supports only ${SharedSecretWrappedSpec::class.java}"
        }
        val otherPublicKeyScheme =
            recoverable("deriveSharedSecret find scheme") { schemeMetadata.findKeyScheme(spec.otherPublicKey) }
        require(spec.keyScheme.canDo(KeySchemeCapability.SHARED_SECRET_DERIVATION)) {
            "The key scheme '${spec.keyScheme}' must support the Diffie–Hellman key agreement."
        }
        require(spec.keyScheme == otherPublicKeyScheme) {
            "The keys must use the same key scheme, publicKey=${spec.keyScheme}, otherPublicKey=$otherPublicKeyScheme"
        }
        logger.info("deriveSharedSecret(spec={})", spec)
        val provider = schemeMetadata.providers.getValue(spec.keyScheme.providerName)
        return recoverable("derviceSharedSecret do deriveDHSharedSecret") {
            deriveDHSharedSecret(
                provider,
                obtainAndStorePrivateKey(spec.publicKey, spec.keyMaterialSpec, computeTenantId(context)),
                spec.otherPublicKey
            )
        }
    }

    override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedWrappedKey {
        val tenantId = computeTenantId(context)
        require(supportedSchemes.containsKey(spec.keyScheme)) {
            "Unsupported key scheme: ${spec.keyScheme.codeName}"
        }
        logger.trace {
            "generateKeyPair(alias=${spec.alias},masterKeyAlias={$spec.masterKeyAlias},scheme={spec.keyScheme.codeName})"
        }
        val keyPairGenerator = keyPairGeneratorFactory(
            spec.keyScheme.algorithmName,
            providerFor(spec.keyScheme)
        )
        val keySize = spec.keyScheme.keySize
        if (spec.keyScheme.algSpec != null) {
            keyPairGenerator.initialize(spec.keyScheme.algSpec, schemeMetadata.secureRandom)
        } else if (keySize != null) {
            keyPairGenerator.initialize(keySize, schemeMetadata.secureRandom)
        }
        val keyPair = keyPairGenerator.generateKeyPair()
        val publicKeySupported = schemeMetadata.toSupportedPublicKey(keyPair.public)

        val wrappingKey = obtainAndStoreWrappingKey(spec.wrappingKeyAlias, tenantId)
        val keyMaterial = wrappingKey.wrap(keyPair.private)
        privateKeyCache?.put(publicKeySupported, keyPair.private)
        return GeneratedWrappedKey(
            publicKey = publicKeySupported,
            keyMaterial = keyMaterial,
            encodingVersion = PRIVATE_KEY_ENCODING_VERSION
        )
    }


    @Suppress("LongParameterList", "ThrowsCount")
    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String?,
        externalId: String?,
        scheme: KeyScheme,
        context: Map<String, String>,
    ): GeneratedWrappedKey {
        logger.info("generateKeyPair(tenant={}, category={}, alias={})", tenantId, category, alias)
        val parentKeyAlias =
            context.get("parentKeyAlias") ?: tenantInfoService.lookup(tenantId, category)?.masterKeyAlias
            ?: throw InvalidParamsException("The tenant '$tenantId' is not configured for category '$category'.")

        signingRepositoryFactory.getInstance(tenantId).use { repo ->
            if (alias != null && repo.findKey(alias) != null) {
                throw KeyAlreadyExistsException(
                    "The key with alias $alias already exists for tenant $tenantId",
                    alias,
                    tenantId
                )
            }
            logger.trace(
                "generateKeyPair for tenant={}, category={}, alias={} using wrapping key ${parentKeyAlias}",
                tenantId,
                category,
                alias
            )

            return generateKeyPair(
                KeyGenerationSpec(scheme, alias, parentKeyAlias),
                context + mapOf(
                    CRYPTO_TENANT_ID to tenantId,
                    CRYPTO_CATEGORY to category
                )
            ).also { key ->
                val saveContext = SigningWrappedKeySaveContext(
                    key = key,
                    wrappingKeyAlias = parentKeyAlias,
                    externalId = externalId,
                    alias = alias,
                    keyScheme = scheme,
                    category = category
                )
                repo.savePrivateKey(saveContext) 
            }
        }
    }

    private fun shortHashOf(key: PublicKey): ShortHash {
        val keyBytes = schemeMetadata.encodeAsByteArray(key)
        val shortHash = ShortHash.of(publicKeyIdFromBytes(keyBytes))
        return shortHash
    }

    private fun computeTenantId(context: Map<String, String>) = context.get("tenantId") ?: CryptoTenants.CRYPTO

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>,
    ): DigitalSignatureWithKey {
        val record =
            CordaMetrics.Metric.Crypto.GetOwnedKeyRecordTimer.builder()
                .withTag(CordaMetrics.Tag.OperationName, SIGN_OPERATION_NAME)
                .withTag(CordaMetrics.Tag.PublicKeyType, publicKey::class.java.simpleName)
                .build()
                .recordCallable {
                    getOwnedKeyRecord(tenantId, publicKey)
                }!!

        logger.debug { "sign(tenant=$tenantId, publicKey=${record.data.id})" }
        val scheme = schemeMetadata.findKeyScheme(record.data.schemeCodeName)
        val spec =
            SigningWrappedSpec(getKeySpec(record, publicKey, tenantId), record.publicKey, scheme, signatureSpec, record.data.category)
        val signedBytes = sign(spec, data, context + mapOf(CRYPTO_TENANT_ID to tenantId))
        return DigitalSignatureWithKey(record.publicKey, signedBytes)
    }

    override fun sign(spec: SigningWrappedSpec, data: ByteArray, context: Map<String, String>): ByteArray {
        val startTime = System.nanoTime()
        val category = context["category"]
        require(category.isNullOrEmpty() || CryptoConsts.Categories.all.contains(category)) {
            "Category value $category is not recognised"
        }
        require(category.isNullOrEmpty() || spec.category.equals(category)) {
            "Provided category $category does not match the key's category ${spec.category}"
        }
        require(data.isNotEmpty()) {
            "Signing of an empty array is not permitted."
        }
        require(supportedSchemes.containsKey(spec.keyScheme)) {
            "Unsupported key scheme: ${spec.keyScheme.codeName}"
        }
        require(spec.keyScheme.canDo(KeySchemeCapability.SIGN)) {
            "Key scheme: ${spec.keyScheme.codeName} cannot be used for signing."
        }
        logger.debug { "sign(spec=$spec)" }
        val privateKey = obtainAndStorePrivateKey(spec.publicKey, spec.keyMaterialSpec, computeTenantId(context))
        val signingData = spec.signatureSpec.getSigningData(digestService, data)
        val signatureBytes = recoverable("sign") {
            if (spec.signatureSpec is CustomSignatureSpec && spec.keyScheme.algorithmName == "RSA") {
                // when the hash is precalculated and the key is RSA the actual sign operation is encryption
                val cipher = Cipher.getInstance(spec.signatureSpec.signatureName, providerFor(spec.keyScheme))
                cipher.init(Cipher.ENCRYPT_MODE, privateKey)
                cipher.doFinal(signingData)
            } else {
                signatureInstances.withSignature(spec.keyScheme, spec.signatureSpec) { signature ->
                    spec.signatureSpec.getParamsSafely()?.let { params -> signature.setParameter(params) }
                    signature.initSign(privateKey, schemeMetadata.secureRandom)
                    signature.update(signingData)
                    signature.sign()
                }
            }
        }
        CordaMetrics.Metric.Crypto.SignTimer
            .builder()
            .withTag(CordaMetrics.Tag.SignatureSpec, spec.signatureSpec.signatureName)
            .build()
            .record(Duration.ofNanos(System.nanoTime() - startTime))
        return signatureBytes
    }

    private fun providerFor(scheme: KeyScheme): Provider =
        schemeMetadata.providers.getValue(scheme.providerName)

    private fun obtainAndStoreWrappingKey(alias: String, tenantId: String): WrappingKey =
        recoverable("obtainAndStoreWrappingKey") {
            CordaMetrics.Metric.Crypto.WrappingKeyCreationTimer.builder()
                .withTag(CordaMetrics.Tag.Tenant, tenantId)
                .build()
                .recordCallable {
                    wrappingKeyCache?.getIfPresent(alias) ?: run {
                        // use IllegalArgumentException instead for not found?
                        val wrappingKeyInfo =
                            wrappingRepositoryFactory.create(tenantId).use { it.findKey(alias) }
                                ?: throw IllegalStateException("Wrapping key with alias $alias not found")
                        require(wrappingKeyInfo.encodingVersion == WRAPPING_KEY_ENCODING_VERSION) {
                            "Unknown wrapping key encoding. Expected to be $WRAPPING_KEY_ENCODING_VERSION"
                        }
                        val parentKey = unmanagedWrappingKeys.get(wrappingKeyInfo.parentKeyAlias)
                        if (parentKey == null) {
                            throw IllegalStateException("Unknown parent key ${wrappingKeyInfo.parentKeyAlias} for $alias")
                        }
                        // TODO remove this restriction? Different levels of wrapping key could sensibly use different algorithms
                        require(parentKey.algorithm == wrappingKeyInfo.algorithmName) {
                            "Expected algorithm is ${parentKey.algorithm} but was ${wrappingKeyInfo.algorithmName}"
                        }

                        parentKey.unwrapWrappingKey(wrappingKeyInfo.keyMaterial).also {
                            wrappingKeyCache?.put(alias, it)
                        }
                    }
                }!!
        }

    private fun obtainAndStorePrivateKey(
        publicKey: PublicKey,
        spec: KeyMaterialSpec,
        tenantId: String
    ): PrivateKey =
        privateKeyCache?.getIfPresent(publicKey) ?: obtainAndStoreWrappingKey(
            spec.wrappingKeyAlias,
            tenantId
        ).unwrap(
            spec.keyMaterial
        ).also {
            privateKeyCache?.put(publicKey, it)
        }


    override fun querySigningKeys(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: KeyOrderBy,
        filter: Map<String, String>,
    ): Collection<SigningKeyInfo> {
        logger.debug {
            "lookup(tenantId=$tenantId, skip=$skip, take=$take, orderBy=$orderBy, filter=[${filter.keys.joinToString()}]"
        }
        // It isn't easy to use the cache here, since the cache is keyed only by public key and we are
        // querying
        // TODO scan the SigningKeyInfo values we have first in cache?
        return signingRepositoryFactory.getInstance(tenantId).use {
            it.query(
                skip,
                take,
                SigningKeyOrderBy.valueOf(orderBy.name),
                filter
            )
        }
    }

    override fun lookupSigningKeysByPublicKeyShortHash(
        tenantId: String,
        keyIds: List<ShortHash>,
    ): Collection<SigningKeyInfo> {
        // Since we are looking up by short IDs there's nothing we can do to handle clashes
        // on short IDs in this case, at this layer. We must therefore rely on the database
        // uniqueness constraints to stop clashes from being created.

        val cachedKeys =
            shortHashCache.getAllPresent(keyIds.mapTo(mutableSetOf()) {
                ShortHashCacheKey(tenantId, it)
            }).mapTo(mutableSetOf()) { it.value }
        val notFound: List<ShortHash> = keyIds - cachedKeys.map { it.id }.toSet()
        if (notFound.isEmpty()) return cachedKeys
        val fetchedKeys = signingRepositoryFactory.getInstance(tenantId).use {
            it.lookupByPublicKeyShortHashes(notFound.toMutableSet())
        }
        fetchedKeys.forEach { shortHashCache.put(ShortHashCacheKey(tenantId, it.id), it) }

        return cachedKeys + fetchedKeys
    }

    override fun lookupSigningKeysByPublicKeyHashes(
        tenantId: String,
        fullKeyIds: List<SecureHash>,
    ): Collection<SigningKeyInfo> {
        fun filterOutMismatches(found: Collection<SigningKeyInfo>) =
            found.map { foundSigningKeyInfo ->
                if (fullKeyIds.contains(foundSigningKeyInfo.fullId)) {
                    foundSigningKeyInfo
                } else {
                    null
                }
            }.filterNotNull()

        val keyIds = fullKeyIds.map { ShortHash.of(it) }
        val cachedMap = shortHashCache.getAllPresent(keyIds.mapTo(mutableSetOf()) { ShortHashCacheKey(tenantId, it) })
        val cachedKeys = cachedMap.map { it.value }
        val cachedMatchingKeys = filterOutMismatches(cachedKeys)
        if (cachedMatchingKeys.size == fullKeyIds.size) return cachedMatchingKeys
        val notFound = fullKeyIds.filter { hash -> cachedMatchingKeys.count { it.fullId == hash } == 0 }
        // if fullKeyIds has duplicates it's possible that notFound is empty here, even though
        // we didn't get as many records from the cache as the number of keys we expected
        if (notFound.isEmpty()) return cachedMatchingKeys

        val fetchedKeys = signingRepositoryFactory.getInstance(tenantId).use {
            it.lookupByPublicKeyHashes(notFound.toMutableSet())
        }
        val fetchedMatchingKeys = filterOutMismatches(fetchedKeys)
        fetchedMatchingKeys.forEach {
            shortHashCache.put(ShortHashCacheKey(tenantId, it.id), it)
        }
        return cachedMatchingKeys + fetchedMatchingKeys
    }

    override fun createWrappingKey(
        hsmId: String,
        failIfExists: Boolean,
        masterKeyAlias: String,
        context: Map<String, String>,
    ) {
        logger.debug {
            "createWrappingKey(hsmId=$hsmId,masterKeyAlias=$masterKeyAlias,failIfExists=$failIfExists," +
                    "onBehalf=${context[CRYPTO_TENANT_ID]})"
        }
        createWrappingKey(masterKeyAlias, failIfExists, context)
    }



    override fun deriveSharedSecret(
        tenantId: String,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        context: Map<String, String>,
    ): ByteArray {
        val record = getOwnedKeyRecord(tenantId, publicKey)

        logger.info(
            "deriveSharedSecret(tenant={}, publicKey={}, otherPublicKey={})",
            tenantId,
            record.data.id,
            otherPublicKey.publicKeyId()
        )
        val scheme = schemeMetadata.findKeyScheme(record.data.schemeCodeName)
        val spec =
            SharedSecretWrappedSpec(
                getKeySpec(record, publicKey, tenantId),
                record.publicKey,
                scheme,
                otherPublicKey
            )
        return deriveSharedSecret(spec, context + mapOf(CRYPTO_TENANT_ID to tenantId))
    }

    @Suppress("ThrowsCount")
    private fun getOwnedKeyRecord(tenantId: String, publicKey: PublicKey): OwnedKeyRecord {
        if (publicKey is CompositeKey) {
            val found = lookupSigningKeysByPublicKeyHashes(tenantId, publicKey.leafKeys.map { it.fullIdHash() })
            val hit = found.firstOrNull()
            if (hit != null) return OwnedKeyRecord(publicKey.leafKeys.filter { it.fullIdHash() == hit.fullId }
                .first(), hit)
            throw IllegalArgumentException(
                "The tenant $tenantId doesn't own any public key in '${publicKey.publicKeyId()}' composite key."
            )
        }
        // now we are not dealing with composite keys
        val requestedFullKeyId = publicKey.fullIdHash(schemeMetadata, digestService)
        val keyId = ShortHash.of(requestedFullKeyId)
        val cacheKey = ShortHashCacheKey(tenantId, keyId)
        val signingKeyInfo = shortHashCache.getIfPresent(cacheKey) ?: run {
            signingRepositoryFactory.getInstance(tenantId).use { repo ->
                repo.findKey(publicKey)
            } ?: throw IllegalArgumentException("The public key '${publicKey.publicKeyId()}' was not found")
        }

        return OwnedKeyRecord(publicKey, signingKeyInfo)
    }
    @Suppress("ThrowsCount")
    private fun getKeySpec(
        record: OwnedKeyRecord,
        publicKey: PublicKey,
        tenantId: String,
    ): KeyMaterialSpec {
        val keyMaterial: ByteArray = record.data.keyMaterial
        val masterKeyAlias = record.data.wrappingKeyAlias 
        val encodingVersion = record.data.encodingVersion ?: throw IllegalStateException(
            "The encoding version for public key ${publicKey.publicKeyId()} of tenant $tenantId must be specified, but is null"
        )
        return KeyMaterialSpec(
            keyMaterial = keyMaterial,
            wrappingKeyAlias = masterKeyAlias,
            encodingVersion = encodingVersion
        )
    }
    
    override fun rewrapWrappingKey(tenantId: String, targetAlias: String, newParentKeyAlias: String) {
        val newParentKey = checkNotNull(unmanagedWrappingKeys.get(newParentKeyAlias)) {
            "Unable to find parent key $newParentKeyAlias in the configured unmanaged wrapping keys"
        }
        wrappingRepositoryFactory.create(tenantId).use { wrappingRepo ->
            val (id, wrappingKeyInfo) = checkNotNull(wrappingRepo.findKeyAndId(targetAlias)) {
                "Wrapping key with alias $targetAlias not found"
            }
            // Find the current unmanaged parent key passed in via config, so we can decrypt the wrapping key
            val oldParentKey = checkNotNull(unmanagedWrappingKeys.get(wrappingKeyInfo.parentKeyAlias)) {
                "Unable to find parent key ${wrappingKeyInfo.parentKeyAlias} in the configured unmanaged wrapping keys"
            }
            oldParentKey.unwrapWrappingKey(wrappingKeyInfo.keyMaterial).also { wrappingKey ->
                logger.trace { "Should decrypt key material in row $id with alias $targetAlias using " +
                        "${wrappingKeyInfo.parentKeyAlias} and encrypt key material using $newParentKeyAlias" }
                val wrappedWithNewKey = newParentKey.wrap(wrappingKey)
                wrappingRepo.saveKeyWithId(
                    targetAlias,
                    wrappingKeyInfo.copy(keyMaterial = wrappedWithNewKey, parentKeyAlias = newParentKeyAlias),
                    id)
            }
        }
    }
    
    override fun close() {
    }

}

