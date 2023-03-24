package net.corda.crypto.service.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.security.InvalidParameterException
import java.security.PublicKey
import java.util.concurrent.TimeUnit
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.crypto.cipher.suite.CRYPTO_CATEGORY
import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.cipher.suite.SharedSecretAliasSpec
import net.corda.crypto.cipher.suite.SharedSecretWrappedSpec
import net.corda.crypto.cipher.suite.SigningAliasSpec
import net.corda.crypto.cipher.suite.SigningWrappedSpec
import net.corda.crypto.cipher.suite.publicKeyId
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.core.KEY_LOOKUP_INPUT_ITEMS_LIMIT
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.persistence.SigningKeyInfo
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.KeyOrderBy
import net.corda.crypto.service.SigningService
import net.corda.crypto.softhsm.SigningRepositoryFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.utilities.debug
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import org.slf4j.LoggerFactory

data class CacheKey(val tenantId: String, val publicKeyId: ShortHash)

private const val SIGNING_SERVICE_OBJ = "signingService"


/**
 * 1. Provide a (mostly) cached versions of the signing key operations, unlike the signing key operations
 * in [SigningRepositoryImpl] which are uncached.
 *
 * 2. Provide wrapping key operations, routing to the appropriate crypto service implementation. The crypto
 *    service wrapping key operations are cached. TODO - remove these and have callers use CryptoServiceFactory?
 */
@Suppress("TooManyFunctions", "LongParameterList")
class SigningServiceImpl(
    private val cryptoServiceFactory: CryptoServiceFactory,
    private val signingRepositoryFactory: SigningRepositoryFactory,
    override val schemeMetadata: CipherSchemeMetadata,
    private val digestService: PlatformDigestService,
    private val cache: Cache<CacheKey, SigningKeyInfo>,
) : SigningService {

    /**
     * Secondary constructor used for production purposes, so that the caller does not need to set the
     * cache up themselves but can instead just supply the config.
     */
    @Suppress("LongParameterList")
    constructor(
        cryptoServiceFactory: CryptoServiceFactory,
        signingRepositoryFactory: SigningRepositoryFactory,
        schemeMetadata: CipherSchemeMetadata,
        digestService: PlatformDigestService,
        config: SmartConfig,
    ) : this(
        cryptoServiceFactory,
        signingRepositoryFactory,
        schemeMetadata,
        digestService,
        CacheFactoryImpl().build(
            "Signing-Key-Cache",
            Caffeine.newBuilder()
                .expireAfterAccess(
                    config.getConfig(SIGNING_SERVICE_OBJ).getConfig("cache").getLong("expireAfterAccessMins"),
                    TimeUnit.MINUTES
                )
                .maximumSize(config.getConfig(SIGNING_SERVICE_OBJ).getConfig("cache").getLong("maximumSize"))
        )
    )

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    data class OwnedKeyRecord(val publicKey: PublicKey, val data: net.corda.crypto.persistence.SigningKeyInfo)

    override fun getSupportedSchemes(tenantId: String, category: String): List<String> {
        logger.debug { "getSupportedSchemes(tenant=$tenantId, category=$category)" }
        val ref = cryptoServiceFactory.findInstance(tenantId = tenantId, category = category)
        return ref.instance.supportedSchemes.map { it.key.codeName }
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
        return signingRepositoryFactory.getInstance(tenantId).query(
            skip,
            take,
            orderBy.toSigningKeyOrderBy(),
            filter
        )
    }

    override fun lookupSigningKeysByPublicKeyShortHash(
        tenantId: String,
        keyIds: List<ShortHash>,
    ): Collection<SigningKeyInfo> {
        val cachedKeys =
            cache.getAllPresent(keyIds.mapTo(mutableSetOf()) {
                CacheKey(tenantId, it)
            }).mapTo(mutableSetOf()) { it.value }
        if (cachedKeys.size == keyIds.size) return cachedKeys
        val notFound: List<ShortHash> = keyIds - cachedKeys.map { it.id }.toSet()

        val fetchedKeys = with(signingRepositoryFactory.getInstance(tenantId)) {
            lookupByPublicKeyShortHashes(notFound.toMutableSet())
        }
        fetchedKeys.forEach { cache.put(CacheKey(tenantId, it.id), it) }

        return cachedKeys + fetchedKeys
    }


    override fun lookupSigningKeysByPublicKeyHashes(
        tenantId: String,
        fullKeyIds: List<SecureHash>,
    ): Collection<SigningKeyInfo> {
        val keyIds = fullKeyIds.map { ShortHash.of(it) }
        val cachedMap = cache.getAllPresent(keyIds.mapTo(mutableSetOf()) { CacheKey(tenantId, it) })
        val cachedList = cachedMap.map { it.value }
        if (cachedMap.size == fullKeyIds.size) return cachedList

        val notFound = fullKeyIds.filter {
            !cachedMap.containsKey(CacheKey(tenantId, ShortHash.of(it)))
        }

        val fetchedKeys = signingRepositoryFactory.getInstance(tenantId).use {
            it.lookupByPublicKeyHashes(notFound.toMutableSet())
                .map { foundKey ->
                    foundKey.also {
                        cache.put(CacheKey(tenantId, it.id), it)
                    }
                }
        }

        return cachedList + fetchedKeys
    }

    // TODO- ditch this method and have callers use crytpoServiceFactory directly?
    // so far all our code has been about signing keys and now we start dealing with wrapping keys.
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
        cryptoServiceFactory.getInstance(hsmId).createWrappingKey(masterKeyAlias, failIfExists, context)
    }

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        scheme: KeyScheme,
        context: Map<String, String>,
    ): PublicKey =
        doGenerateKeyPair(
            tenantId = tenantId,
            category = category,
            alias = alias,
            externalId = null,
            scheme = scheme,
            context = context
        )

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        externalId: String,
        scheme: KeyScheme,
        context: Map<String, String>,
    ): PublicKey =
        doGenerateKeyPair(
            tenantId = tenantId,
            category = category,
            alias = alias,
            externalId = externalId,
            scheme = scheme,
            context = context
        )

    override fun freshKey(
        tenantId: String,
        category: String,
        scheme: KeyScheme,
        context: Map<String, String>,
    ): PublicKey =
        doGenerateKeyPair(
            tenantId = tenantId,
            category = category,
            alias = null,
            externalId = null,
            scheme = scheme,
            context = context
        )

    override fun freshKey(
        tenantId: String,
        category: String,
        externalId: String,
        scheme: KeyScheme,
        context: Map<String, String>,
    ): PublicKey =
        doGenerateKeyPair(
            tenantId = tenantId,
            category = category,
            alias = null,
            externalId = externalId,
            scheme = scheme,
            context = context
        )

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>,
    ): DigitalSignature.WithKey {
        val record = getOwnedKeyRecord(tenantId, publicKey)
        logger.debug { "sign(tenant=$tenantId, publicKey=${record.data.id})" }
        val scheme = schemeMetadata.findKeyScheme(record.data.schemeCodeName)
        val cryptoService = cryptoServiceFactory.getInstance(record.data.hsmId)
        val spec = if (record.data.keyMaterial != null)
            SigningWrappedSpec(getKeySpec(record, publicKey, tenantId), record.publicKey, scheme, signatureSpec)
        else
            SigningAliasSpec(getHsmAlias(record, publicKey, tenantId), publicKey, scheme, signatureSpec)
        val signedBytes = cryptoService.sign(spec, data, context + mapOf(CRYPTO_TENANT_ID to tenantId))
        return DigitalSignature.WithKey(record.publicKey, signedBytes)
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
        val cryptoService = cryptoServiceFactory.getInstance(record.data.hsmId)
        val spec = if (record.data.keyMaterial != null)
            SharedSecretWrappedSpec(getKeySpec(record, publicKey, tenantId), record.publicKey, scheme, otherPublicKey)
        else
            SharedSecretAliasSpec(getHsmAlias(record, publicKey, tenantId), record.publicKey, scheme, otherPublicKey)
        return cryptoService.deriveSharedSecret(spec, context + mapOf(CRYPTO_TENANT_ID to tenantId))
    }

    private fun getHsmAlias(
        record: OwnedKeyRecord,
        publicKey: PublicKey,
        tenantId: String,
    ): String = record.data.hsmAlias ?: throw IllegalStateException(
        "HSM alias must be specified if key material is not specified, and both are null for ${publicKey.publicKeyId()} of tenant $tenantId"
    )

    @Suppress("LongParameterList")
    private fun doGenerateKeyPair(
        tenantId: String,
        category: String,
        alias: String?,
        externalId: String?,
        scheme: KeyScheme,
        context: Map<String, String>,
    ): PublicKey {
        logger.info("generateKeyPair(tenant={}, category={}, alias={}))", tenantId, category, alias)
        val ref = cryptoServiceFactory.findInstance(tenantId = tenantId, category = category)
        val repo = signingRepositoryFactory.getInstance(tenantId)
        if (alias != null && repo.findKey(alias) != null) {
            throw KeyAlreadyExistsException(
                "The key with alias $alias already exists for tenant $tenantId",
                alias,
                tenantId
            )
        }
        require(ref.masterKeyAlias != null) { "The master key alias must be defined for tenant $tenantId category $category" }
        val generatedKey = ref.instance.generateKeyPair(
            KeyGenerationSpec(scheme, alias, ref.masterKeyAlias),
            context + mapOf(
                CRYPTO_TENANT_ID to tenantId,
                CRYPTO_CATEGORY to category
            )
        )
        val saveContext = ref.toSaveKeyContext(generatedKey, alias, scheme, externalId)
        val signingKeyInfo = when (saveContext) {
            is SigningWrappedKeySaveContext -> repo.savePrivateKey(saveContext)
            is SigningPublicKeySaveContext -> repo.savePublicKey(saveContext)
            else -> throw InvalidParameterException()
        }
        cache.put(CacheKey(tenantId, signingKeyInfo.id), signingKeyInfo)
        return schemeMetadata.toSupportedPublicKey(generatedKey.publicKey)
    }

    @Suppress("NestedBlockDepth")
    private fun getOwnedKeyRecord(tenantId: String, publicKey: PublicKey): OwnedKeyRecord {
        if (publicKey is CompositeKey) {
            val leafKeysIdsChunks = publicKey.leafKeys.map {
                it.fullIdHash(schemeMetadata, digestService) to it
            }.chunked(KEY_LOOKUP_INPUT_ITEMS_LIMIT)
            for (chunk in leafKeysIdsChunks) {
                val found = signingRepositoryFactory.getInstance(tenantId).lookupByPublicKeyHashes(
                    chunk.map { it.first }.toMutableSet()
                )
                if (found.isNotEmpty()) {
                    for (key in chunk) {
                        val first = found.firstOrNull { it.fullId == key.first }
                        if (first != null) {
                            return OwnedKeyRecord(key.second, first)
                        }
                    }
                }
            }
            throw IllegalArgumentException(
                "The tenant $tenantId doesn't own any public key in '${publicKey.publicKeyId()}' composite key."
            )
        } else {
            // TODO - use cache?
            return signingRepositoryFactory.getInstance(tenantId).findKey(publicKey)?.let {
                // This is to make sure cached key by short id (db one looks with full id so should be OK) is the actual
                // requested key Sand not a different one that clashed on key id (short key id).
                if (it.fullId == publicKey.fullIdHash(schemeMetadata, digestService)) {
                    it
                } else {
                    null
                }
            }?.let {
                OwnedKeyRecord(publicKey, it)
            } ?: throw IllegalArgumentException(
                "The tenant $tenantId doesn't own public key '${publicKey.publicKeyId()}'."
            )
        }
    }

    @Suppress("ThrowsCount")
    private fun getKeySpec(
        record: OwnedKeyRecord,
        publicKey: PublicKey,
        tenantId: String,
    ): KeyMaterialSpec {
        val keyMaterial: ByteArray = record.data.keyMaterial ?: throw IllegalStateException(
            "The key material is null for public key ${publicKey.publicKeyId()} of tenant $tenantId  "
        )
        val masterKeyAlias = record.data.masterKeyAlias ?: throw IllegalStateException(
            "The master key alias for public key ${publicKey.publicKeyId()} of tenant $tenantId must be specified, but is null"
        )
        val encodingVersion = record.data.encodingVersion ?: throw IllegalStateException(
            "The encoding version for public key ${publicKey.publicKeyId()} of tenant $tenantId must be specified, but is null"
        )
        return KeyMaterialSpec(
            keyMaterial = keyMaterial,
            wrappingKeyAlias = masterKeyAlias,
            encodingVersion = encodingVersion
        )
    }
}

private fun KeyOrderBy.toSigningKeyOrderBy(): SigningKeyOrderBy =
    SigningKeyOrderBy.valueOf(name)
