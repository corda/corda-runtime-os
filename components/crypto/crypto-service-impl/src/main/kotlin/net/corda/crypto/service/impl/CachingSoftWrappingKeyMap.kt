package net.corda.crypto.service.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.WrappingKeyStore
import net.corda.crypto.softhsm.SoftCacheConfig
import net.corda.crypto.softhsm.SoftWrappingKeyMap
import net.corda.crypto.softhsm.WRAPPING_KEY_ENCODING_VERSION
import java.util.concurrent.TimeUnit

class CachingSoftWrappingKeyMap(
    config: SoftCacheConfig,
    private val store: WrappingKeyStore,
    private val master: WrappingKey
) : SoftWrappingKeyMap {
    private val cache: Cache<String, WrappingKey> = CacheFactoryImpl().build(
        "HSM-Wrapping-Keys-Map",
        Caffeine.newBuilder()
            .expireAfterAccess(config.expireAfterAccessMins, TimeUnit.MINUTES)
            .maximumSize(config.maximumSize))

    override fun getWrappingKey(alias: String): WrappingKey = cache.get(alias) {
        val wrappingKeyInfo = store.findWrappingKey(alias)
            ?: throw IllegalStateException("The $alias is not created yet.")
        require(wrappingKeyInfo.encodingVersion == WRAPPING_KEY_ENCODING_VERSION) {
            "Unknown wrapping key encoding. Expected to be $WRAPPING_KEY_ENCODING_VERSION"
        }
        require(master.algorithm == wrappingKeyInfo.algorithmName) {
            "Expected algorithm is ${master.algorithm} but was ${wrappingKeyInfo.algorithmName}"
        }
        master.unwrapWrappingKey(wrappingKeyInfo.keyMaterial)
    }

    override fun putWrappingKey(alias: String, wrappingKey: WrappingKey) {
        val wrappingKeyInfo = WrappingKeyInfo(
            encodingVersion = WRAPPING_KEY_ENCODING_VERSION,
            algorithmName = wrappingKey.algorithm,
            keyMaterial = master.wrap(wrappingKey)
        )
        store.saveWrappingKey(alias, wrappingKeyInfo)
        cache.put(alias, wrappingKey)
    }

    override fun exists(alias: String): Boolean =
        cache.getIfPresent(alias) != null || store.findWrappingKey(alias) != null
}