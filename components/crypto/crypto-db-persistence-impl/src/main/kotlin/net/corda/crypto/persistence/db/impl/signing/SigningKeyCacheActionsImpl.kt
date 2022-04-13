package net.corda.crypto.persistence.db.impl.signing

import com.github.benmanes.caffeine.cache.Cache
import net.corda.crypto.core.publicKeyIdOf
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyCacheActions
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
import net.corda.crypto.persistence.db.model.SigningKeyEntityPrimaryKey
import net.corda.crypto.persistence.externalId
import net.corda.crypto.persistence.masterKeyAlias
import net.corda.crypto.persistence.schemeCodeName
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.v5.cipher.suite.KeyEncodingService
import java.security.PublicKey
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.PersistenceException
import javax.persistence.TypedQuery
import javax.persistence.criteria.Predicate
import kotlin.reflect.KProperty

/**
 * Implementation deliberately caches only keys which are requested by 'find](publicKey: PublicKey' function.
 *
 * As not all databases support unique constrains which ignore null indexed values the implementation relies on
 * that the generation of the keys suing aliases is quite rare occasion and that the existence check is done
 * upstream by the higher services.
 */
class SigningKeyCacheActionsImpl(
    private val tenantId: String,
    private val entityManager: EntityManager,
    private val cache: Cache<String, SigningCachedKey>,
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
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
                    externalId = context.externalId
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
            trx.commit()
        } catch (e: PersistenceException) {
            trx.rollback()
            throw e
        } catch (e: Throwable) {
            trx.rollback()
            throw PersistenceException("Failed to save", e)
        }
    }

    override fun find(alias: String): SigningCachedKey? {
        val result = findByAliases(listOf(alias))
        if (result.size > 1) {
            throw IllegalStateException(
                "There are more than one key with alias=$alias for tenant=$tenantId"
            )
        }
        return result.firstOrNull()?.toSigningCachedKey()
    }

    override fun find(publicKey: PublicKey): SigningCachedKey? =
        cache.get(publicKeyIdOf(publicKey)) {
            entityManager.find(
                SigningKeyEntity::class.java, SigningKeyEntityPrimaryKey(
                    tenantId = tenantId,
                    keyId = it
                )
            )?.toSigningCachedKey()
        }

    override fun lookup(
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filter: Map<String, String>
    ): Collection<SigningCachedKey> {
        val map = layeredPropertyMapFactory.create<SigningKeyFilterMapImpl>(filter)
        val builder = LookupBuilder(entityManager)
        builder.equal(SigningKeyEntity::tenantId, tenantId)
        builder.equal(SigningKeyEntity::category, map.category)
        builder.equal(SigningKeyEntity::schemeCodeName, map.schemeCodeName)
        builder.equal(SigningKeyEntity::alias, map.alias)
        builder.equal(SigningKeyEntity::masterKeyAlias, map.masterKeyAlias)
        builder.equal(SigningKeyEntity::externalId, map.externalId)
        builder.greaterThanOrEqualTo(SigningKeyEntity::created, map.createdAfter)
        builder.lessThanOrEqualTo(SigningKeyEntity::created, map.createdBefore)
        return builder.build(skip, take, orderBy).resultList.map {
            it.toSigningCachedKey()
        }
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
            it.toSigningCachedKey()
        }.distinctBy {
            it.id
        }
    }

    override fun close() {
        entityManager.close()
    }

    private fun findByIds(ids: Collection<String>): Collection<SigningKeyEntity> {
        return entityManager.createQuery(
            "from SigningKeyEntity where tenantId=:tenantId AND keyId IN(:ids)",
            SigningKeyEntity::class.java
        ).also {
            it.setParameter("tenantId", tenantId)
            it.setParameter("ids", ids)
        }.resultList
    }

    private fun findByAliases(aliases: Collection<String>): Collection<SigningKeyEntity> =
        entityManager.createQuery(
            "from SigningKeyEntity where tenantId=:tenantId AND alias IN(:aliases)",
            SigningKeyEntity::class.java
        ).also {
            it.setParameter("tenantId", tenantId)
            it.setParameter("aliases", aliases)
        }.resultList

    private fun SigningKeyEntity.toSigningCachedKey(): SigningCachedKey =
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

    private class LookupBuilder(
        private val entityManager: EntityManager
    ) {
        private val cb = entityManager.criteriaBuilder
        private val cr = cb.createQuery(SigningKeyEntity::class.java)
        private val root = cr.from(SigningKeyEntity::class.java)
        private val predicates = mutableListOf<Predicate>()

        fun <T> equal(property: KProperty<T?>, value: T?) {
            if (value != null) {
                predicates.add(cb.equal(root.get<T>(property.name), value))
            }
        }

        fun <T : Comparable<T>> greaterThanOrEqualTo(property: KProperty<T?>, value: T?) {
            if (value != null) {
                predicates.add(
                    cb.greaterThanOrEqualTo(root.get(property.name), value)
                )
            }
        }

        fun <T : Comparable<T>> lessThanOrEqualTo(property: KProperty<T?>, value: T?) {
            if (value != null) {
                predicates.add(
                    cb.lessThanOrEqualTo(root.get(property.name), value)
                )
            }
        }

        fun build(skip: Int, take: Int, orderBy: SigningKeyOrderBy): TypedQuery<SigningKeyEntity> {
            cr.where(cb.and(*predicates.toTypedArray()))
            when (orderBy) {
                SigningKeyOrderBy.NONE -> Unit
                SigningKeyOrderBy.ID -> ascOrderBy(SigningKeyEntity::keyId)
                SigningKeyOrderBy.CREATED -> ascOrderBy(SigningKeyEntity::created)
                SigningKeyOrderBy.CATEGORY -> ascOrderBy(SigningKeyEntity::category)
                SigningKeyOrderBy.SCHEME_CODE_NAME -> ascOrderBy(SigningKeyEntity::schemeCodeName)
                SigningKeyOrderBy.ALIAS -> ascOrderBy(SigningKeyEntity::alias)
                SigningKeyOrderBy.MASTER_KEY_ALIAS -> ascOrderBy(SigningKeyEntity::masterKeyAlias)
                SigningKeyOrderBy.EXTERNAL_ID -> ascOrderBy(SigningKeyEntity::externalId)
                SigningKeyOrderBy.CREATED_DESC -> descOrderBy(SigningKeyEntity::created)
                SigningKeyOrderBy.CATEGORY_DESC -> descOrderBy(SigningKeyEntity::category)
                SigningKeyOrderBy.SCHEME_CODE_NAME_DESC -> descOrderBy(SigningKeyEntity::schemeCodeName)
                SigningKeyOrderBy.ALIAS_DESC -> descOrderBy(SigningKeyEntity::alias)
                SigningKeyOrderBy.MASTER_KEY_ALIAS_DESC -> descOrderBy(SigningKeyEntity::masterKeyAlias)
                SigningKeyOrderBy.EXTERNAL_ID_DESC -> descOrderBy(SigningKeyEntity::externalId)
                SigningKeyOrderBy.ID_DESC -> descOrderBy(SigningKeyEntity::keyId)
            }
            return entityManager.createQuery(cr)
                .setFirstResult(skip)
                .setMaxResults(take)
        }

        private fun <T> ascOrderBy(property: KProperty<T>) {
            cr.orderBy(cb.asc(root.get<T>(property.name)))
        }

        private fun <T> descOrderBy(property: KProperty<T>) {
            cr.orderBy(cb.desc(root.get<T>(property.name)))
        }
    }
}