package net.corda.crypto.persistence.impl

import net.corda.crypto.core.ShortHash
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyStatus
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.v5.crypto.SecureHash
import javax.persistence.EntityManager

object SigningKeysRepositoryImpl : SigningKeysRepository {

    override fun findKeysByIds(
        entityManager: EntityManager,
        tenantId: String,
        keyIds: Set<ShortHash>
    ): Collection<SigningCachedKey> {
        val keyIdsStrings = keyIds.map { it.value }
        return entityManager.createQuery(
            "FROM SigningKeyEntity WHERE tenantId=:tenantId AND keyId IN(:keyIds)",
            SigningKeyEntity::class.java
        ).setParameter("tenantId", tenantId)
            .setParameter("keyIds", keyIdsStrings)
            .resultList.map { it.toSigningCachedKey() }
    }

    override fun findKeysByFullIds(
        entityManager: EntityManager,
        tenantId: String,
        fullKeyIds: Set<SecureHash>
    ): Collection<SigningCachedKey> {
        val fullKeyIdsStrings = fullKeyIds.map { it.toString() }
        return entityManager.createQuery(
            "FROM ${SigningKeyEntity::class.java.simpleName} " +
                    "WHERE tenantId=:tenantId " +
                    "AND fullKeyId IN(:fullKeyIds) " +
                    "ORDER BY timestamp",
            SigningKeyEntity::class.java
        ).setParameter("tenantId", tenantId)
            .setParameter("fullKeyIds", fullKeyIdsStrings)
            .resultList.map { it.toSigningCachedKey() }
    }

    override fun findKeyByFullId(
        entityManager: EntityManager,
        tenantId: String,
        fullKeyId: SecureHash
    ): SigningCachedKey? =
        entityManager.createQuery(
            "FROM ${SigningKeyEntity::class.java.simpleName} " +
                    "WHERE tenantId=:tenantId " +
                    "AND fullKeyId=:fullKeyId",
            SigningKeyEntity::class.java
        ).setParameter("tenantId", tenantId)
            .setParameter("fullKeyId", fullKeyId.toString())
            .resultList.singleOrNull()?.toSigningCachedKey()

    override fun findByAliases(
        entityManager: EntityManager,
        tenantId: String,
        aliases: Collection<String>
    ): Collection<SigningKeyEntity> =
        entityManager.createQuery(
            "FROM SigningKeyEntity WHERE tenantId=:tenantId AND alias IN(:aliases)",
            SigningKeyEntity::class.java
        ).setParameter("tenantId", tenantId)
            .setParameter("aliases", aliases)
            .resultList
}

fun SigningKeyEntity.toSigningCachedKey(): SigningCachedKey =
    SigningCachedKey(
        id = keyId,
        fullId = fullKeyId,
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
