package net.corda.crypto.softhsm.impl

import java.security.PublicKey
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.core.KEY_LOOKUP_INPUT_ITEMS_LIMIT
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.core.fullPublicKeyIdFromBytes
import net.corda.crypto.core.parseSecureHash
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.persistence.SigningKeyFilterMapImpl
import net.corda.crypto.persistence.SigningKeyInfo
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeyStatus
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
import net.corda.crypto.softhsm.SigningRepository
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.v5.crypto.SecureHash
import java.time.Instant
import javax.persistence.EntityManagerFactory

@Suppress("LongParameterList")
class SigningRepositoryImpl(
    private val entityManagerFactory: EntityManagerFactory,
    private val tenantId: String,
    private val keyEncodingService: KeyEncodingService,
    private val digestService: PlatformDigestService,
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
) : SigningRepository {
    override fun close() = entityManagerFactory.close()

    /**
     * If short key id clashes with existing key for this [tenantId], [save] will fail. It will fail upon
     * persisting to the DB due to unique constraint of <tenant id, short key id>.
     */
    override fun savePublicKey(context: SigningPublicKeySaveContext): SigningKeyInfo {
        val publicKeyBytes = keyEncodingService.encodeAsByteArray(context.key.publicKey)
        val keyId = publicKeyIdFromBytes(publicKeyBytes)
        val fullKeyId = fullPublicKeyIdFromBytes(publicKeyBytes, digestService)
        val entity = SigningKeyEntity(
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

        entityManagerFactory.createEntityManager().transaction {
            it.persist(entity)
        }
        return entity.toSigningKeyInfo()
    }

    override fun savePrivateKey(context: SigningWrappedKeySaveContext): SigningKeyInfo {
        val publicKeyBytes = keyEncodingService.encodeAsByteArray(context.key.publicKey)
        val keyId = publicKeyIdFromBytes(publicKeyBytes)
        val fullKeyId = fullPublicKeyIdFromBytes(publicKeyBytes, digestService)
        val entity = SigningKeyEntity(
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

        entityManagerFactory.createEntityManager().transaction {
            it.persist(entity)
        }
        return entity.toSigningKeyInfo()
    }

    override fun findKey(alias: String): SigningKeyInfo? {
        val result = entityManagerFactory.createEntityManager().use { em ->
            em.createQuery(
                "FROM SigningKeyEntity WHERE tenantId=:tenantId AND alias=:alias",
                SigningKeyEntity::class.java
            ).setParameter("tenantId", tenantId)
                .setParameter("alias", alias)
                .resultList
        }

        if (result.size > 1) {
            throw IllegalStateException("There are more than one key with alias=$alias for tenant=$tenantId")
        }
        return result.firstOrNull()?.toSigningKeyInfo()
    }

    override fun findKey(publicKey: PublicKey): SigningKeyInfo? {
        val requestedFullKeyId = publicKey.fullIdHash(keyEncodingService, digestService)
        return entityManagerFactory.createEntityManager().use { em ->
            em.createQuery(
                "FROM ${SigningKeyEntity::class.java.simpleName} " +
                        "WHERE tenantId=:tenantId " +
                        "AND fullKeyId=:fullKeyId",
                SigningKeyEntity::class.java
            ).setParameter("tenantId", tenantId)
                .setParameter("fullKeyId", requestedFullKeyId.toString())
                .resultList.singleOrNull()?.toSigningKeyInfo()
            em.createQuery<SigningKeyEntity?>(
                "FROM ${SigningKeyEntity::class.java.simpleName} " +
                        "WHERE tenantId=:tenantId " +
                        "AND fullKeyId=:fullKeyId",
                SigningKeyEntity::class.java
            ).setParameter("tenantId", tenantId)
                .setParameter("fullKeyId", requestedFullKeyId.toString())
                .resultList.singleOrNull<SigningKeyEntity?>()?.toSigningKeyInfo()
        }
    }

    override fun query(
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filter: Map<String, String>,
    ): Collection<SigningKeyInfo> = entityManagerFactory.createEntityManager().use { em ->
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
            it.toSigningKeyInfo()
        }
    }

    override fun lookupByPublicKeyShortHashes(keyIds: Set<ShortHash>): Collection<SigningKeyInfo> {
        require(keyIds.size <= KEY_LOOKUP_INPUT_ITEMS_LIMIT) {
            "The number of ids exceeds $KEY_LOOKUP_INPUT_ITEMS_LIMIT"
        }
        return entityManagerFactory.createEntityManager().use { em ->
            val keyIdsStrings = keyIds.map<ShortHash, String> { it.value }
            em.createQuery<SigningKeyEntity?>(
                "FROM SigningKeyEntity WHERE tenantId=:tenantId AND keyId IN(:keyIds)",
                SigningKeyEntity::class.java
            ).setParameter("tenantId", tenantId)
                .setParameter("keyIds", keyIdsStrings)
                .resultList.map { it.toSigningKeyInfo() }
        }
    }

    override fun lookupByPublicKeyHashes(fullKeyIds: Set<SecureHash>): Collection<SigningKeyInfo> {
        require(fullKeyIds.size <= KEY_LOOKUP_INPUT_ITEMS_LIMIT) {
            "The number of ids exceeds $KEY_LOOKUP_INPUT_ITEMS_LIMIT"
        }

        return entityManagerFactory.createEntityManager().use { em ->
            val fullKeyIdsStrings = fullKeyIds.map { it.toString() }
            em.createQuery<SigningKeyEntity?>(
                "FROM ${SigningKeyEntity::class.java.simpleName} " +
                        "WHERE tenantId=:tenantId " +
                        "AND fullKeyId IN(:fullKeyIds) " +
                        "ORDER BY timestamp",
                SigningKeyEntity::class.java
            )
                .setParameter("tenantId", tenantId)
                .setParameter("fullKeyIds", fullKeyIdsStrings)
                .resultList.map { it.toSigningKeyInfo() }
        }
    }
}


fun SigningKeyEntity.toSigningKeyInfo(): SigningKeyInfo =
    SigningKeyInfo(
        id = ShortHash.parse(keyId),
        fullId = parseSecureHash(fullKeyId),
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
        hsmId = hsmId,
        status = SigningKeyStatus.valueOf(status.name)
    )