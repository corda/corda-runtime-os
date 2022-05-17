package net.corda.crypto.persistence.db.impl.soft

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.crypto.persistence.soft.SoftCryptoKeyCache
import net.corda.crypto.persistence.soft.SoftCryptoKeyCacheActions
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.impl.config.CryptoSoftPersistenceConfig
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManagerFactory

class SoftCryptoKeyCacheImpl(
    config: CryptoSoftPersistenceConfig,
    private val entityManagerFactory: EntityManagerFactory,
    private val masterKey: WrappingKey
) : SoftCryptoKeyCache {
    private val cache: Cache<String, WrappingKey> = Caffeine.newBuilder()
        .expireAfterAccess(config.expireAfterAccessMins, TimeUnit.MINUTES)
        .maximumSize(config.maximumSize)
        .build()

    override fun act(): SoftCryptoKeyCacheActions =
        SoftCryptoKeyCacheActionsImpl(
            entityManagerFactory.createEntityManager(),
            cache,
            masterKey
        )

    override fun close() {
        cache.invalidateAll()
        cache.cleanUp()
    }
}