package net.corda.crypto.persistence.db.impl.soft

import com.github.benmanes.caffeine.cache.Cache
import net.corda.crypto.persistence.soft.SoftCryptoKeyCacheActions
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.persistence.db.impl.doInTransaction
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import java.time.Instant
import javax.persistence.EntityManager

class SoftCryptoKeyCacheActionsImpl(
    private val entityManager: EntityManager,
    private val cache: Cache<String, WrappingKey>,
    private val master: WrappingKey
) : SoftCryptoKeyCacheActions {
    override fun saveWrappingKey(alias: String, key: WrappingKey, failIfExists: Boolean) {
        val entity = WrappingKeyEntity(
            alias = alias,
            created = Instant.now(),
            encodingVersion = 1,
            algorithmName = key.algorithm,
            keyMaterial = master.wrap(key)
        )
        entityManager.doInTransaction {
            if(!(!failIfExists && exists(alias))) {
                entityManager.persist(entity)
            }
        }
    }

    override fun findWrappingKey(alias: String): WrappingKey? =
        cache.get(alias) {
            find(alias)?.let {
                master.unwrapWrappingKey(it.keyMaterial)
            }
        }

    override fun close() {
        entityManager.close()
    }

    private fun find(alias: String): WrappingKeyEntity? = entityManager.find(WrappingKeyEntity::class.java, alias)

    private fun exists(alias: String): Boolean = find(alias) != null
}