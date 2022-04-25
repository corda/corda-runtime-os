package net.corda.crypto.persistence.db.impl.soft

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.crypto.persistence.SoftCryptoKeyCache
import net.corda.crypto.persistence.SoftCryptoKeyCacheActions
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.persistence.config.softPersistence
import net.corda.libs.configuration.SmartConfig
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManagerFactory

class SoftCryptoKeyCacheImpl(
    config: SmartConfig,
    private val entityManagerFactory: EntityManagerFactory,
    private val masterKey: WrappingKey
) : SoftCryptoKeyCache {
    private val cache: Cache<String, WrappingKey> = Caffeine.newBuilder()
        .expireAfterAccess(config.softPersistence.expireAfterAccessMins, TimeUnit.MINUTES)
        .maximumSize(config.softPersistence.maximumSize)
        .build()

    override fun act(): SoftCryptoKeyCacheActions =
        SoftCryptoKeyCacheActionsImpl(
            entityManagerFactory.createEntityManager(),
            cache,
            masterKey
        )

    override fun close() {
        cache.invalidateAll()
    }
}