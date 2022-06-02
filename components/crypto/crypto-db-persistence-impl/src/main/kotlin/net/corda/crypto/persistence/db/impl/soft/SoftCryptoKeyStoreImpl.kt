package net.corda.crypto.persistence.db.impl.soft

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.crypto.persistence.soft.SoftCryptoKeyStore
import net.corda.crypto.persistence.soft.SoftCryptoKeyStoreActions
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.impl.config.CryptoSoftPersistenceConfig
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManagerFactory

class SoftCryptoKeyStoreImpl(
    config: CryptoSoftPersistenceConfig,
    private val entityManagerFactory: EntityManagerFactory,
    private val masterKey: WrappingKey
) : SoftCryptoKeyStore {
    private val cache: Cache<String, WrappingKey> = Caffeine.newBuilder()
        .expireAfterAccess(config.expireAfterAccessMins, TimeUnit.MINUTES)
        .maximumSize(config.maximumSize)
        .build()

    override fun act(): SoftCryptoKeyStoreActions =
        SoftCryptoKeyStoreActionsImpl(
            entityManagerFactory.createEntityManager(),
            cache,
            masterKey
        )

    override fun close() {
        cache.invalidateAll()
        cache.cleanUp()
    }
}