package net.corda.crypto.persistence.db.impl.soft

import com.github.benmanes.caffeine.cache.Cache
import net.corda.crypto.persistence.SoftCryptoKeyCacheActions
import net.corda.crypto.persistence.WrappingKey
import javax.persistence.EntityManager

class SoftCryptoKeyCacheActionsImpl(
    private val entityManager: EntityManager,
    private val cache: Cache<String, WrappingKey>,
    private val masterKey: WrappingKey
) : SoftCryptoKeyCacheActions {
    override fun saveWrappingKey(alias: String, key: WrappingKey) {
        TODO("Not yet implemented")
    }

    override fun findWrappingKey(alias: String): WrappingKey? =
        cache.get(alias) {
            val record = entityManager.find(Any::class.java, alias)
            null
        }

    override fun close() {
        entityManager.close()
    }
}