package net.corda.crypto.softhsm.impl

import javax.persistence.EntityManager
import net.corda.crypto.core.ShortHash
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.v5.crypto.SecureHash

// Adding interface so it can be mocked in tests
interface SigningKeysRepository {
    fun findKeysByIds(
        entityManager: EntityManager,
        tenantId: String,
        keyIds: Set<ShortHash>
    ): Collection<SigningCachedKey>

    fun findKeysByFullIds(
        entityManager: EntityManager,
        tenantId: String,
        fullKeyIds: Set<SecureHash>
    ): Collection<SigningCachedKey>

    fun findKeyByFullId(
        entityManager: EntityManager,
        tenantId: String,
        fullKeyId: SecureHash
    ): SigningCachedKey?

    fun findByAliases(
        entityManager: EntityManager,
        tenantId: String,
        aliases: Collection<String>
    ): Collection<SigningKeyEntity>
}