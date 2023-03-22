package net.corda.crypto.softhsm.impl

import com.github.benmanes.caffeine.cache.Cache
import java.security.PublicKey
import java.time.Instant
import javax.persistence.EntityManagerFactory
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.core.KEY_LOOKUP_INPUT_ITEMS_LIMIT
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.core.fullPublicKeyIdFromBytes
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyFilterMapImpl
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeySaveContext
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.persistence.alias
import net.corda.crypto.persistence.category
import net.corda.crypto.persistence.createdAfter
import net.corda.crypto.persistence.createdBefore
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.crypto.persistence.db.model.SigningKeyEntityStatus
import net.corda.crypto.persistence.externalId
import net.corda.crypto.persistence.masterKeyAlias
import net.corda.crypto.persistence.schemeCodeName
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.crypto.SecureHash

@Suppress("LongParameterList")
class V1SigningKeyStore(
    private val cache: Cache<CacheKey, SigningCachedKey>,
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    private val keyEncodingService: KeyEncodingService,
    private val entityManagerFactory: EntityManagerFactory,
    private val digestService: PlatformDigestService,
    private val signingKeysRepository: SigningKeysRepository,
) {

    data class CacheKey(val tenantId: String, val publicKeyId: ShortHash)

    /**
     * If short key id clashes with existing key for this [tenantId], [save] will fail. It will fail upon
     * persisting to the DB due to unique constraint of <tenant id, short key id>.
     */
    fun save(tenantId: String, context: SigningKeySaveContext) {
        val keyId: String
        val fullKeyId: String
        val entity = when (context) {
            is SigningPublicKeySaveContext -> {
                val publicKeyBytes = keyEncodingService.encodeAsByteArray(context.key.publicKey)
                keyId = publicKeyIdFromBytes(publicKeyBytes)
                fullKeyId = fullPublicKeyIdFromBytes(publicKeyBytes, digestService)
                SigningKeyEntity(
                    tenantId = tenantId,
                    keyId = keyId,
                    fullKeyId = fullKeyId,
                    timestamp = Instant.now(),
                    category = context.category,
                    schemeCodeName = context.keyScheme.codeName,
                    publicKey = publicKeyBytes,
                    keyMaterial = null,
                    encodingVersion = null,
                    masterKeyAlias = null,
                    alias = context.alias,
                    hsmAlias = context.key.hsmAlias,
                    externalId = context.externalId,
                    hsmId = context.hsmId,
                    status = SigningKeyEntityStatus.NORMAL
                )
            }

            is SigningWrappedKeySaveContext -> {
                val publicKeyBytes = keyEncodingService.encodeAsByteArray(context.key.publicKey)
                keyId = publicKeyIdFromBytes(publicKeyBytes)
                fullKeyId = fullPublicKeyIdFromBytes(publicKeyBytes, digestService)
                SigningKeyEntity(
                    tenantId = tenantId,
                    keyId = keyId,
                    fullKeyId = fullKeyId,
                    timestamp = Instant.now(),
                    category = context.category,
                    schemeCodeName = context.keyScheme.codeName,
                    publicKey = publicKeyBytes,
                    keyMaterial = context.key.keyMaterial,
                    encodingVersion = context.key.encodingVersion,
                    masterKeyAlias = context.masterKeyAlias,
                    alias = context.alias,
                    hsmAlias = null,
                    externalId = context.externalId,
                    hsmId = context.hsmId,
                    status = SigningKeyEntityStatus.NORMAL
                )
            }

            else -> throw IllegalArgumentException("Unknown context type: ${context::class.java.name}")
        }
        entityManagerFactory.createEntityManager().transaction {
            it.persist(entity)
        }
        cache.put(CacheKey(tenantId, ShortHash.of(keyId)), entity.toSigningCachedKey())
    }

    fun find(tenantId: String, alias: String): SigningCachedKey? {
        val result = entityManagerFactory.createEntityManager().use { em ->
            signingKeysRepository.findByAliases(em, tenantId, listOf(alias))
        }

        if (result.size > 1) {
            throw IllegalStateException("There are more than one key with alias=$alias for tenant=$tenantId")
        }
        return result.firstOrNull()?.toSigningCachedKey()
    }

    fun lookupByKey(tenantId: String, publicKey: PublicKey): SigningCachedKey? {
        val requestedFullKeyId = publicKey.fullIdHash(keyEncodingService, digestService)
        return lookupByFullKeyId(tenantId, requestedFullKeyId)
    }

    @VisibleForTesting
    fun lookupByFullKeyId(tenantId: String, requestedFullKeyId: SecureHash): SigningCachedKey? {
        val keyId = ShortHash.of(requestedFullKeyId)
        return cache.get(CacheKey(tenantId, keyId)) {
            entityManagerFactory.createEntityManager().use { em ->
                signingKeysRepository.findKeyByFullId(em, tenantId, requestedFullKeyId)
            }
        }?.let {
            // This is to make sure cached key by short id (db one looks with full id so should be OK) is the actual
            // requested key and not a different one that clashed on key id (short key id).
            if (it.fullId == requestedFullKeyId) {
                it
            } else {
                null
            }
        }
    }

    fun lookup(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filter: Map<String, String>,
    ): Collection<SigningCachedKey> = entityManagerFactory.createEntityManager().use { em ->
        val map = layeredPropertyMapFactory.create<SigningKeyFilterMapImpl>(filter)
        val builder = SigningKeyLookupBuilder(em)
        builder.equal(SigningKeyEntity::tenantId, tenantId)
        builder.equal(SigningKeyEntity::category, map.category)
        builder.equal(SigningKeyEntity::schemeCodeName, map.schemeCodeName)
        builder.equal(SigningKeyEntity::alias, map.alias)
        builder.equal(SigningKeyEntity::masterKeyAlias, map.masterKeyAlias)
        builder.equal(SigningKeyEntity::externalId, map.externalId)
        builder.greaterThanOrEqualTo(SigningKeyEntity::timestamp, map.createdAfter)
        builder.lessThanOrEqualTo(SigningKeyEntity::timestamp, map.createdBefore)
        builder.build(skip, take, orderBy).resultList.map {
            it.toSigningCachedKey()
        }
    }

    fun lookupByKeyIds(tenantId: String, requestedKeyIds: Set<ShortHash>): Collection<SigningCachedKey> {
        require(requestedKeyIds.size <= KEY_LOOKUP_INPUT_ITEMS_LIMIT) {
            "The number of ids exceeds $KEY_LOOKUP_INPUT_ITEMS_LIMIT"
        }

        val cachedKeys =
            cache.getAllPresent(requestedKeyIds.mapTo(mutableSetOf()) { CacheKey(tenantId, it) })
                .mapTo(mutableSetOf()) { it.value }

        return if (cachedKeys.size == requestedKeyIds.size) {
            cachedKeys
        } else {
            val notFound = requestedKeyIds - cachedKeys.mapTo(mutableSetOf()) { it.id }
            val fetchedKeys =
                entityManagerFactory.createEntityManager().use { em ->
                    signingKeysRepository.findKeysByIds(em, tenantId, notFound)
                }

            fetchedKeys.forEach {
                cache.put(CacheKey(tenantId, it.id), it)
            }
            cachedKeys + fetchedKeys
        }
    }

    fun lookupByFullKeyIds(tenantId: String, requestedFullKeyIds: Set<SecureHash>): Collection<SigningCachedKey> {
        require(requestedFullKeyIds.size <= KEY_LOOKUP_INPUT_ITEMS_LIMIT) {
            "The number of ids exceeds $KEY_LOOKUP_INPUT_ITEMS_LIMIT"
        }

        // cache is using short key ids so convert to find cached keys
        val keyIds = requestedFullKeyIds.map { ShortHash.of(it) }
        val cached =
            cache.getAllPresent(keyIds.mapTo(mutableSetOf()) { CacheKey(tenantId, it) })
        // check requested full key ids actually match cached full key ids
        val cachedKeysByFullId =
            cached
                .map {
                    it.value
                }
                .filterTo(mutableSetOf()) {
                    // TODO Clashed keys on short ids should be identified and removed from `requestedFullKeyIds` so we
                    //  don't look them up in DB since short key ids can't clash per tenant,
                    //  i.e. there can't be a different key with same short key id
                    it.fullId in requestedFullKeyIds
                }

        return if (cachedKeysByFullId.size == requestedFullKeyIds.size) {
            cachedKeysByFullId
        } else {
            val notFound =
                requestedFullKeyIds - cachedKeysByFullId.mapTo(mutableSetOf()) { it.fullId }
            // We look for keys in DB by their full key ids so not risking a clash here
            val fetchedKeys =
                entityManagerFactory.createEntityManager().use { em ->
                    signingKeysRepository.findKeysByFullIds(em, tenantId, notFound)
                }
            fetchedKeys.forEach {
                cache.put(CacheKey(tenantId, it.id), it)
            }
            cachedKeysByFullId + fetchedKeys
        }
    }

}