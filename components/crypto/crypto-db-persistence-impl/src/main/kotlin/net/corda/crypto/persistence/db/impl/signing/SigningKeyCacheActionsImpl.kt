package net.corda.crypto.persistence.db.impl.signing

import com.github.benmanes.caffeine.cache.Cache
import net.corda.crypto.core.publicKeyIdOf
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyCacheActions
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeySaveContext
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.crypto.persistence.db.model.SigningKeyEntityPrimaryKey
import java.security.PublicKey
import java.time.Instant
import javax.persistence.EntityManager

class SigningKeyCacheActionsImpl(
    private val tenantId: String,
    private val entityManager: EntityManager,
    private val cache: Cache<String, SigningCachedKey>
) : SigningKeyCacheActions {
    override fun save(context: SigningKeySaveContext) {
        TODO("Not yet implemented")
    }

    override fun find(alias: String): SigningCachedKey? =
        entityManager.createQuery(
            "from SigningKeySaveContext where tenantId='$tenantId' AND alias='$alias'",
            SigningKeyEntity::class.java
        ).resultList.singleOrNull().toSigningCachedKey()

    override fun find(publicKey: PublicKey): SigningCachedKey? =
        cache.get(publicKeyIdOf(publicKey)) {
            entityManager.find(
                SigningKeyEntity::class.java, SigningKeyEntityPrimaryKey(
                    tenantId = tenantId,
                    keyId = it
                )
            ).toSigningCachedKey()
        }

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> {
        TODO("Not yet implemented")
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
    ): List<SigningCachedKey> {
        TODO("Not yet implemented")
    }

    override fun lookup(ids: List<String>): Collection<SigningCachedKey> {
        if(ids.size > 20) {
            throw IllegalArgumentException("The maximum size should not exceed 20 items, received ${ids.size}.")
        }
        val cached = cache.getAllPresent(ids)
        if(cached.size == ids.size) {
            return cached.values
        }
        val notPresent = ids.filter { !cached.containsKey(it) }
        entityManager.createQuery(
            "from SigningKeySaveContext where tenantId='$tenantId' AND keyId='$alias'",
            SigningKeyEntity::class.java
        ).resultList
        TODO("Not yet implemented")
    }

    override fun close() {
        entityManager.close()
    }

    private fun SigningKeyEntity?.toSigningCachedKey(): SigningCachedKey? =
        if(this == null) {
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