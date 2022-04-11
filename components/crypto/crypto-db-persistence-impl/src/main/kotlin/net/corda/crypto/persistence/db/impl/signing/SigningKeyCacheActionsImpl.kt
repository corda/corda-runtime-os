package net.corda.crypto.persistence.db.impl.signing

import com.github.benmanes.caffeine.cache.Cache
import net.corda.crypto.core.publicKeyIdOf
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyCacheActions
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeySaveContext
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.crypto.persistence.db.model.SigningKeyEntityPrimaryKey
import net.corda.v5.cipher.suite.KeyEncodingService
import java.security.PublicKey
import java.time.Instant
import javax.persistence.EntityManager

class SigningKeyCacheActionsImpl(
    private val tenantId: String,
    private val entityManager: EntityManager,
    private val cache: Cache<String, SigningCachedKey>,
    private val keyEncodingService: KeyEncodingService
) : SigningKeyCacheActions {
    override fun save(context: SigningKeySaveContext) {
        val entity = when (context) {
            is SigningPublicKeySaveContext -> {
                val publicKeyBytes = keyEncodingService.encodeAsByteArray(context.key.publicKey)
                SigningKeyEntity(
                    tenantId = tenantId,
                    keyId = publicKeyIdOf(publicKeyBytes),
                    created = Instant.now(),
                    category = context.category,
                    schemeCodeName = context.signatureScheme.codeName,
                    publicKey = publicKeyBytes,
                    keyMaterial = null,
                    encodingVersion = null,
                    masterKeyAlias = null,
                    alias = context.alias,
                    hsmAlias = context.key.hsmAlias,
                    externalId = null
                )
            }
            is SigningWrappedKeySaveContext -> {
                val publicKeyBytes = keyEncodingService.encodeAsByteArray(context.key.publicKey)
                SigningKeyEntity(
                    tenantId = tenantId,
                    keyId = publicKeyIdOf(publicKeyBytes),
                    created = Instant.now(),
                    category = context.category,
                    schemeCodeName = context.signatureScheme.codeName,
                    publicKey = publicKeyBytes,
                    keyMaterial = context.key.keyMaterial,
                    encodingVersion = context.key.encodingVersion,
                    masterKeyAlias = context.masterKeyAlias,
                    alias = context.alias,
                    hsmAlias = null,
                    externalId = context.externalId
                )
            }
            else -> throw IllegalArgumentException("Unknown context type: ${context::class.java.name}")
        }
        val trx = entityManager.transaction
        trx.begin()
        try {
            entityManager.persist(entity)
            if ((!entity.alias.isNullOrBlank()) && findByAliases(listOf(entity.alias!!)).size > 1) {
                throw IllegalArgumentException(
                    "The key with alias=${entity.alias} already exists for tenant=$tenantId"
                )
            }
            trx.commit()
        } catch (e: Throwable) {
            trx.rollback()
            throw e
        }
    }

    override fun find(alias: String): SigningCachedKey? {
        val result = findByAliases(listOf(alias))
        if (result.size > 1) {
            throw IllegalStateException(
                "There are more than one key with alias=$alias for tenant=$tenantId"
            )
        }
        return result.firstOrNull().toSigningCachedKey()
    }

    override fun find(publicKey: PublicKey): SigningCachedKey? =
        cache.get(publicKeyIdOf(publicKey)) {
            entityManager.find(
                SigningKeyEntity::class.java, SigningKeyEntityPrimaryKey(
                    tenantId = tenantId,
                    keyId = it
                )
            ).toSigningCachedKey()
        }

    override fun filterMyKeys(candidateKeys: Collection<PublicKey>): Collection<PublicKey> =
        findByIds(candidateKeys.map { publicKeyIdOf(it) }).map {
            keyEncodingService.decodePublicKey(it.publicKey)
        }

    override fun lookup(
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        category: String?,
        schemeCodeName: String?,
        alias: String?,
        masterKeyAlias: String?,
        createdAfter: Instant?,
        createdBefore: Instant?
    ): Collection<SigningCachedKey> {
        TODO("Not yet implemented")
    }

    override fun lookup(ids: List<String>): Collection<SigningCachedKey> {
        if (ids.size > 20) {
            throw IllegalArgumentException("The maximum size should not exceed 20 items, received ${ids.size}.")
        }
        val cached = cache.getAllPresent(ids)
        if (cached.size == ids.size) {
            return cached.values
        }
        val notFound = ids.filter { id -> !cached.containsKey(id) }
        return cached.values + findByIds(notFound).map {
            it.toSigningCachedKey()!!
        }.distinctBy {
            it.id
        }
    }

    override fun close() {
        entityManager.close()
    }

    private fun findByIds(ids: Collection<String>): Collection<SigningKeyEntity> =
        entityManager.createQuery(
            "from SigningKeySaveContext where tenantId=:tenantId AND keyId IN(:ids)",
            SigningKeyEntity::class.java
        ).also {
            it.setParameter("tenantId", tenantId)
            it.setParameter("ids", ids)
        }.resultList

    private fun findByAliases(aliases: Collection<String>): Collection<SigningKeyEntity> =
        entityManager.createQuery(
            "from SigningKeySaveContext where tenantId=:tenantId AND alias IN(:aliases)",
            SigningKeyEntity::class.java
        ).also {
            it.setParameter("tenantId", tenantId)
            it.setParameter("aliases", aliases)
        }.resultList

    private fun SigningKeyEntity?.toSigningCachedKey(): SigningCachedKey? =
        if (this == null) {
            null
        } else {
            SigningCachedKey(
                id = keyId,
                tenantId = tenantId,
                category = category,
                alias = alias,
                hsmAlias = hsmAlias,
                publicKey = publicKey,
                keyMaterial = keyMaterial,
                schemeCodeName = schemeCodeName,
                masterKeyAlias = masterKeyAlias,
                externalId = externalId,
                encodingVersion = encodingVersion,
                created = created
            )
        }
}