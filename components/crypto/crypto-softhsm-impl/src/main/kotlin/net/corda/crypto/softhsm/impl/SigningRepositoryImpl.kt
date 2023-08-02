package net.corda.crypto.softhsm.impl

import java.security.PublicKey
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.core.InvalidParamsException
import net.corda.crypto.core.KEY_LOOKUP_INPUT_ITEMS_LIMIT
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.core.fullPublicKeyIdFromBytes
import net.corda.crypto.core.parseSecureHash
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.persistence.SigningKeyFilterMapImpl
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.core.SigningKeyStatus
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.persistence.alias
import net.corda.crypto.persistence.category
import net.corda.crypto.persistence.createdAfter
import net.corda.crypto.persistence.createdBefore
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.crypto.persistence.db.model.SigningKeyEntityStatus
import net.corda.crypto.persistence.db.model.SigningKeyMaterialEntity
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.persistence.externalId
import net.corda.crypto.persistence.schemeCodeName
import net.corda.crypto.softhsm.SigningRepository
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.metrics.CordaMetrics
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.v5.crypto.SecureHash
import java.io.InvalidObjectException
import java.time.Instant
import java.util.*
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import net.corda.crypto.core.CryptoConsts

@Suppress("LongParameterList")
class SigningRepositoryImpl(
    private val entityManagerFactory: EntityManagerFactory,
    private val tenantId: String,
    private val keyEncodingService: KeyEncodingService,
    private val digestService: PlatformDigestService,
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
) : SigningRepository {
    override fun close() = entityManagerFactory.close()

    @Suppress("NestedBlockDepth")
    override fun savePrivateKey(context: SigningWrappedKeySaveContext): SigningKeyInfo {
        val publicKeyBytes = keyEncodingService.encodeAsByteArray(context.key.publicKey)
        val keyId = publicKeyIdFromBytes(publicKeyBytes)
        val fullKeyId = fullPublicKeyIdFromBytes(publicKeyBytes, digestService)
        val keyMainId = UUID.randomUUID()
        val now = Instant.now()

        val wrappingKeyId = entityManagerFactory.createEntityManager().use { it ->
            with(it.criteriaBuilder ?: throw InvalidObjectException("could not get criteria builder")) {
                val queryBuilder = with(
                    createQuery(WrappingKeyEntity::class.java)
                        ?: throw InvalidObjectException("unable to create query on wrapping key")
                ) {
                    val root = from(WrappingKeyEntity::class.java)
                    where(
                        equal(
                            root.get<String>("alias"),
                            context.wrappingKeyAlias
                        )
                    ) // do not care about generation for now
                }


                it.createQuery(queryBuilder)
                    .setMaxResults(1).resultList.singleOrNull()
                    ?.let {
                        it.id
                    }
            }
                ?: throw InvalidParamsException("unable to find master wrapping key ${context.wrappingKeyAlias} in tenant $tenantId")
        }

        val materialEntity = SigningKeyMaterialEntity(
            signingKeyId = keyMainId,
            wrappingKeyId = wrappingKeyId,
            created = now,
            keyMaterial = context.key.keyMaterial,
        )

        entityManagerFactory.createEntityManager().use {
            it.transaction {
                it.persist(materialEntity)
            }
        }

        val entity = SigningKeyEntity(
            id = keyMainId,
            tenantId = tenantId,
            keyId = keyId,
            fullKeyId = fullKeyId,
            created = now,
            category = context.category,
            schemeCodeName = context.keyScheme.codeName,
            publicKey = publicKeyBytes,
            encodingVersion = context.key.encodingVersion,
            alias = context.alias,
            hsmAlias = null,
            externalId = context.externalId,
            hsmId = CryptoConsts.SOFT_HSM_ID,
            status = SigningKeyEntityStatus.NORMAL
        )
        entityManagerFactory.createEntityManager().use {
            it.transaction {
                it.persist(entity)
            }
        }

        return entityManagerFactory.createEntityManager().use { entity.joinSigningKeyInfo(it, keyEncodingService) }
    }

    override fun findKey(alias: String): SigningKeyInfo? {
        entityManagerFactory.createEntityManager().use { em ->
            val result = em.createQuery(
                "FROM ${SigningKeyEntity::class.java.simpleName} WHERE tenantId=:tenantId AND alias=:alias",
                SigningKeyEntity::class.java
            ).setParameter("tenantId", tenantId)
                .setParameter("alias", alias)
                .resultList

            if (result.size > 1) {
                throw IllegalStateException("There are more than one key with alias=$alias for tenant=$tenantId")
            }


            return result.firstOrNull()?.joinSigningKeyInfo(em, keyEncodingService)
        }
    }

    override fun findKey(publicKey: PublicKey): SigningKeyInfo? {
        val requestedFullKeyId = publicKey.fullIdHash(keyEncodingService, digestService)
        return entityManagerFactory.createEntityManager().use { em ->
            em.transaction {
                em.createQuery(
                    "FROM ${SigningKeyEntity::class.java.simpleName} " +
                            "WHERE tenantId=:tenantId " +
                            "AND fullKeyId=:fullKeyId",
                    SigningKeyEntity::class.java
                ).setParameter("tenantId", tenantId)
                    .setParameter("fullKeyId", requestedFullKeyId.toString())
                    .resultList.singleOrNull()?.joinSigningKeyInfo(em, keyEncodingService)
            }
        }
    }

    override fun query(
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filter: Map<String, String>,
    ): Collection<SigningKeyInfo> = entityManagerFactory.createEntityManager().use { em ->
        em.transaction {
            val map = layeredPropertyMapFactory.create<SigningKeyFilterMapImpl>(filter)
            val builder = SigningKeyLookupBuilder(em)
            builder.equal(SigningKeyEntity::tenantId, tenantId)
            builder.equal(SigningKeyEntity::category, map.category)
            builder.equal(SigningKeyEntity::schemeCodeName, map.schemeCodeName)
            builder.equal(SigningKeyEntity::alias, map.alias)
            builder.equal(SigningKeyEntity::externalId, map.externalId)
            builder.greaterThanOrEqualTo(SigningKeyEntity::created, map.createdAfter)
            builder.lessThanOrEqualTo(SigningKeyEntity::created, map.createdBefore)
            builder.build(skip, take, orderBy).resultList.map {
                it.joinSigningKeyInfo(em, keyEncodingService)
            }
        }
    }

    override fun lookupByPublicKeyShortHashes(keyIds: Set<ShortHash>): Collection<SigningKeyInfo> {
        require(keyIds.size <= KEY_LOOKUP_INPUT_ITEMS_LIMIT) {
            "The number of ids exceeds $KEY_LOOKUP_INPUT_ITEMS_LIMIT"
        }
        return CordaMetrics.Metric.Crypto.SigningKeyLookupTimer.builder()
            .withTag(CordaMetrics.Tag.SigningKeyLookupMethod, "PublicKeyShortHashes")
            .build()
            .recordCallable {
                entityManagerFactory.createEntityManager().use { em ->
                    em.transaction {
                        val keyIdsStrings = keyIds.map<ShortHash, String> { it.value }
                        em.createQuery<SigningKeyEntity?>(
                            "FROM SigningKeyEntity WHERE tenantId=:tenantId AND keyId IN(:keyIds)",
                            SigningKeyEntity::class.java
                        ).setParameter("tenantId", tenantId)
                            .setParameter("keyIds", keyIdsStrings)
                            .resultList.map { it.joinSigningKeyInfo(em, keyEncodingService) }
                    }
                }
            }!!
    }

    override fun lookupByPublicKeyHashes(fullKeyIds: Set<SecureHash>): Collection<SigningKeyInfo> {
        require(fullKeyIds.size <= KEY_LOOKUP_INPUT_ITEMS_LIMIT) {
            "The number of ids exceeds $KEY_LOOKUP_INPUT_ITEMS_LIMIT"
        }

        return CordaMetrics.Metric.Crypto.SigningKeyLookupTimer.builder()
            .withTag(CordaMetrics.Tag.SigningKeyLookupMethod, "PublicKeyHashes")
            .build()
            .recordCallable {
                entityManagerFactory.createEntityManager().use { em ->
                    em.transaction {
                        val fullKeyIdsStrings = fullKeyIds.map { it.toString() }

                        em.createQuery<SigningKeyEntity?>(
                            "FROM ${SigningKeyEntity::class.java.simpleName} " +
                                    "WHERE tenantId=:tenantId " +
                                    "AND fullKeyId IN(:fullKeyIds) " +
                                    "ORDER BY created",
                            SigningKeyEntity::class.java
                        )
                            .setParameter("tenantId", tenantId)
                            .setParameter("fullKeyIds", fullKeyIdsStrings)
                            .resultList.map { it.joinSigningKeyInfo(em, keyEncodingService) }
                    }
                }
            }!!
    }
}


fun SigningKeyEntity.joinSigningKeyInfo(em: EntityManager, keyEncodingService: KeyEncodingService): SigningKeyInfo {
    val signingKeyMaterialEntity = checkNotNull(em.createQuery(
        "FROM ${SigningKeyMaterialEntity::class.java.simpleName} WHERE signingKeyId=:signingKeyId",
        SigningKeyMaterialEntity::class.java
    ).setParameter("signingKeyId", id)
        .resultList.singleOrNull()) { "private key material for $id not found"}
    val wrappingKey = checkNotNull(em.createQuery(
        "FROM WrappingKeyEntity WHERE id=:wrappingKeyId", WrappingKeyEntity::class.java
    ).setParameter("wrappingKeyId", signingKeyMaterialEntity.wrappingKeyId).resultList.singleOrNull()) { 
        "wrapping key for $id not found"
    }

    return SigningKeyInfo(
        id = ShortHash.parse(keyId),
        fullId = parseSecureHash(fullKeyId),
        tenantId = tenantId,
        category = category,
        alias = alias,
        hsmAlias = hsmAlias,
        publicKey = keyEncodingService.decodePublicKey(publicKey),
        keyMaterial = signingKeyMaterialEntity.keyMaterial,
        schemeCodeName = schemeCodeName,
        wrappingKeyAlias = wrappingKey.alias,
        externalId = externalId,
        encodingVersion = encodingVersion,
        timestamp = created,
        hsmId = hsmId,
        status = SigningKeyStatus.valueOf(status.name)
    )
}