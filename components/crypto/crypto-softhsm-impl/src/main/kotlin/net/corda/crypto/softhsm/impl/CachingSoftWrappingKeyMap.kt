package net.corda.crypto.softhsm.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.persistence.CryptoConnectionsFactory
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.softhsm.SoftCacheConfig
import net.corda.crypto.softhsm.SoftWrappingKeyMap
import net.corda.crypto.softhsm.WRAPPING_KEY_ENCODING_VERSION
import net.corda.orm.utils.use
import net.corda.orm.utils.transaction
import java.time.Instant
import java.util.concurrent.TimeUnit


class CachingSoftWrappingKeyMap(
    config: SoftCacheConfig,
    private val master: WrappingKey,
    private val connectionsFactory: CryptoConnectionsFactory
) : SoftWrappingKeyMap {
    private val cache: Cache<String, WrappingKey> = CacheFactoryImpl().build(
        "HSM-Wrapping-Keys-Map",
        Caffeine.newBuilder()
            .expireAfterAccess(config.expireAfterAccessMins, TimeUnit.MINUTES)
            .maximumSize(config.maximumSize)
    )

    private fun entityManagerFactory() = connectionsFactory.getEntityManagerFactory(CryptoTenants.CRYPTO)

    private fun findWrappingKey(alias: String): WrappingKeyEntity? = entityManagerFactory().use { em ->
        em.find(WrappingKeyEntity::class.java, alias)
    }

    override fun getWrappingKey(alias: String): WrappingKey = cache.get(alias) {
        val key = findWrappingKey(alias) ?: throw IllegalStateException("$alias not found")
        require(key.encodingVersion == WRAPPING_KEY_ENCODING_VERSION) {
            "Unknown wrapping key encoding. Expected to be $WRAPPING_KEY_ENCODING_VERSION"
        }
        require(master.algorithm == key.algorithmName) {
            "Expected algorithm is ${master.algorithm} but was ${key.algorithmName}"
        }
        master.unwrapWrappingKey(key.keyMaterial)
    }

    override fun putWrappingKey(alias: String, wrappingKey: WrappingKey) {
        entityManagerFactory().transaction { em ->
            em.persist(
                WrappingKeyEntity(
                    alias = alias,
                    created = Instant.now(),
                    encodingVersion = WRAPPING_KEY_ENCODING_VERSION,
                    algorithmName = wrappingKey.algorithm,
                    keyMaterial = master.wrap(wrappingKey)
                )
            )
        }
        cache.put(alias, wrappingKey)
    }

    override fun exists(alias: String): Boolean = cache.getIfPresent(alias) != null || findWrappingKey(alias) != null
}