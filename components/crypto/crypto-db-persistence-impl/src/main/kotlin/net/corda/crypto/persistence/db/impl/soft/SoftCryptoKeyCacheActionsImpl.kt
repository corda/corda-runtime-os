package net.corda.crypto.persistence.db.impl.soft

import com.github.benmanes.caffeine.cache.Cache
import net.corda.crypto.persistence.SoftCryptoKeyCacheActions
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityTransaction
import javax.persistence.PersistenceException

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
        val trx = entityManager.transaction
        trx.begin()
        try {
            if(!failIfExists && exists(alias)) {
                return
            }
            entityManager.persist(entity)
            trx.commit()
        } catch (e: PersistenceException) {
            safelyRollback(trx)
            throw e
        } catch (e: Throwable) {
            safelyRollback(trx)
            throw PersistenceException("Failed to save", e)
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

    private fun safelyRollback(trx: EntityTransaction) {
        try {
            trx.rollback()
        } catch (e: Throwable) {
            // intentional
        }
    }

    private fun find(alias: String): WrappingKeyEntity? = entityManager.find(WrappingKeyEntity::class.java, alias)

    private fun exists(alias: String): Boolean = find(alias) != null
}