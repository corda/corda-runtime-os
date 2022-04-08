package net.corda.crypto.persistence.db.impl.soft

import com.github.benmanes.caffeine.cache.Cache
import net.corda.crypto.persistence.SoftCryptoKeyCache
import net.corda.crypto.persistence.SoftCryptoKeyCacheActions
import net.corda.crypto.core.aes.WrappingKey
import javax.persistence.EntityManagerFactory

class SoftCryptoKeyCacheImpl(
    private val entityManagerFactory: EntityManagerFactory,
    private val cache: Cache<String, WrappingKey>,
    private val masterKey: WrappingKey
) : SoftCryptoKeyCache {
    override fun act(): SoftCryptoKeyCacheActions =
        SoftCryptoKeyCacheActionsImpl(
            entityManagerFactory.createEntityManager(),
            cache,
            masterKey
        )
}