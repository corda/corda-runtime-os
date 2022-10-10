package net.corda.crypto.service.cipher.suite.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.service.PlatformCipherSuiteMetadata
import net.corda.crypto.service.cipher.suite.GeneratedWrappingKey
import net.corda.crypto.service.cipher.suite.SoftCacheConfig
import net.corda.crypto.service.cipher.suite.WrappingKeyMap
import net.corda.crypto.service.cipher.suite.WRAPPING_KEY_ENCODING_VERSION
import net.corda.crypto.service.persistence.WrappingKeyInfo
import net.corda.crypto.service.persistence.WrappingKeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit

class CachingWrappingKeyMap(
    config: SoftCacheConfig,
    private val metadata: PlatformCipherSuiteMetadata,
    private val store: WrappingKeyStore,
    private val master: WrappingKey
) : WrappingKeyMap {
    private val cache: Cache<String, WrappingKey> = CacheFactoryImpl().build(
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

    override fun getWrappingKey(): GeneratedWrappingKey {
        // for now - generate the key each time
        val wrappingKey = WrappingKey.generateWrappingKey(metadata)
        val wrappingKeyInfo = WrappingKeyInfo(
            encodingVersion = WRAPPING_KEY_ENCODING_VERSION,
            alias = UUID.randomUUID().toString(),
            algorithmName = wrappingKey.algorithm,
            keyMaterial = master.wrap(wrappingKey)
        )
        store.saveWrappingKey(wrappingKeyInfo)
        cache.put(wrappingKeyInfo.alias, wrappingKey)
        return GeneratedWrappingKey(
            alias = wrappingKeyInfo.alias,
            key = wrappingKey
        )
    }
}