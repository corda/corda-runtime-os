package net.corda.crypto.persistence.db.impl.soft

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.crypto.persistence.SoftCryptoKeyCache
import net.corda.crypto.persistence.SoftCryptoKeyCacheActions
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.impl.config.softPersistence
import net.corda.libs.configuration.SmartConfig
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManagerFactory

class SoftCryptoKeyCacheImpl(
    config: SmartConfig,
    private val entityManagerFactory: EntityManagerFactory,
    private val masterKey: WrappingKey
) : SoftCryptoKeyCache {
    private val cache: Cache<String, WrappingKey> = buildCache(config)

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

    private fun buildCache(config: SmartConfig): Cache<String, WrappingKey> {
        val persistenceConfig = config.softPersistence()
        return Caffeine.newBuilder()
            .expireAfterAccess(persistenceConfig.expireAfterAccessMins, TimeUnit.MINUTES)
            .maximumSize(persistenceConfig.maximumSize)
            .build()
    }
}