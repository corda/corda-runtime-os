package net.corda.crypto.persistence.db.impl.signing

import com.github.benmanes.caffeine.cache.Cache
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.persistence.signing.SigningCachedKey
import net.corda.crypto.persistence.signing.SigningKeyCacheActions
import net.corda.crypto.persistence.signing.SigningKeyFilterMapImpl
import net.corda.crypto.persistence.signing.SigningKeyOrderBy
import net.corda.crypto.persistence.signing.SigningKeySaveContext
import net.corda.crypto.persistence.signing.SigningPublicKeySaveContext
import net.corda.crypto.persistence.signing.SigningWrappedKeySaveContext
import net.corda.crypto.persistence.signing.alias
import net.corda.crypto.persistence.signing.category
import net.corda.crypto.persistence.signing.createdAfter
import net.corda.crypto.persistence.signing.createdBefore
import net.corda.crypto.persistence.db.impl.doInTransaction
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.crypto.persistence.db.model.SigningKeyEntityPrimaryKey
import net.corda.crypto.persistence.db.model.SigningKeyEntityStatus
import net.corda.crypto.persistence.signing.SigningKeyStatus
import net.corda.crypto.persistence.signing.externalId
import net.corda.crypto.persistence.signing.masterKeyAlias
import net.corda.crypto.persistence.signing.schemeCodeName
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.KEY_LOOKUP_INPUT_ITEMS_LIMIT
import net.corda.v5.crypto.publicKeyId
import java.security.PublicKey
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.TypedQuery
import javax.persistence.criteria.Predicate
import kotlin.reflect.KProperty

/**
 * Implementation deliberately caches only keys which are requested by 'find(publicKey: PublicKey' function.
 *
 * As not all databases support unique constrains which ignore null indexed values the implementation relies on
 * that the generation of the keys using aliases is quite rare occasion and that the existence check is done
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
                    keyId = publicKeyIdFromBytes(publicKeyBytes),
                    timestamp = Instant.now(),
                    category = context.category,
                    schemeCodeName = context.signatureScheme.codeName,
                    publicKey = publicKeyBytes,
                    keyMaterial = null,
                    encodingVersion = null,
                    masterKeyAlias = null,
                    alias = context.alias,
                    hsmAlias = context.key.hsmAlias,
                    externalId = context.externalId,
                    associationId = context.associationId,
                    status = SigningKeyEntityStatus.NORMAL
                )
            }
            is SigningWrappedKeySaveContext -> {
                val publicKeyBytes = keyEncodingService.encodeAsByteArray(context.key.publicKey)
                SigningKeyEntity(
                    tenantId = tenantId,
                    keyId = publicKeyIdFromBytes(publicKeyBytes),
                    timestamp = Instant.now(),
                    category = context.category,
                    schemeCodeName = context.signatureScheme.codeName,
                    publicKey = publicKeyBytes,
                    keyMaterial = context.key.keyMaterial,
                    encodingVersion = context.key.encodingVersion,
                    masterKeyAlias = context.masterKeyAlias,
                    alias = context.alias,
                    hsmAlias = null,
                    externalId = context.externalId,
                    associationId = context.associationId,
                    status = SigningKeyEntityStatus.NORMAL
                )
            }
            else -> throw IllegalArgumentException("Unknown context type: ${context::class.java.name}")
        }
        entityManager.doInTransaction {
            entityManager.persist(entity)
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
        cache.get(publicKey.publicKeyId()) {
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
        builder.greaterThanOrEqualTo(SigningKeyEntity::timestamp, map.createdAfter)
        builder.lessThanOrEqualTo(SigningKeyEntity::timestamp, map.createdBefore)
        return builder.build(skip, take, orderBy).resultList.map {
            it.toSigningCachedKey()
        }
    }

    override fun lookup(ids: List<String>): Collection<SigningCachedKey> {
        require (ids.size <= KEY_LOOKUP_INPUT_ITEMS_LIMIT) {
            "The number of ids exceeds $KEY_LOOKUP_INPUT_ITEMS_LIMIT"
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
            timestamp = timestamp,
            associationId = associationId,
            status = SigningKeyStatus.valueOf(status.name)
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

        @Suppress("SpreadOperator", "ComplexMethod")
        fun build(skip: Int, take: Int, orderBy: SigningKeyOrderBy): TypedQuery<SigningKeyEntity> {
            cr.where(cb.and(*predicates.toTypedArray()))
            when (orderBy) {
                SigningKeyOrderBy.NONE -> Unit
                SigningKeyOrderBy.ID -> ascOrderBy(SigningKeyEntity::keyId)
                SigningKeyOrderBy.TIMESTAMP -> ascOrderBy(SigningKeyEntity::timestamp)
                SigningKeyOrderBy.CATEGORY -> ascOrderBy(SigningKeyEntity::category)
                SigningKeyOrderBy.SCHEME_CODE_NAME -> ascOrderBy(SigningKeyEntity::schemeCodeName)
                SigningKeyOrderBy.ALIAS -> ascOrderBy(SigningKeyEntity::alias)
                SigningKeyOrderBy.MASTER_KEY_ALIAS -> ascOrderBy(SigningKeyEntity::masterKeyAlias)
                SigningKeyOrderBy.EXTERNAL_ID -> ascOrderBy(SigningKeyEntity::externalId)
                SigningKeyOrderBy.TIMESTAMP_DESC -> descOrderBy(SigningKeyEntity::timestamp)
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