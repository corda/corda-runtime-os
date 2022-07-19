package net.corda.crypto.service.impl.softhsm

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.impl.config.CryptoSoftHSMConfig
import net.corda.crypto.persistence.wrapping.WrappingKeyInfo
import net.corda.crypto.persistence.wrapping.WrappingKeyStore
import net.corda.crypto.service.softhsm.SoftWrappingKeyMap
import net.corda.crypto.service.softhsm.WRAPPING_KEY_ENCODING_VERSION
import java.util.concurrent.TimeUnit

class CachingSoftWrappingKeyMap(
    config: CryptoSoftHSMConfig.Cache,
    private val store: WrappingKeyStore,
    private val master: WrappingKey
) : SoftWrappingKeyMap {
    private val cache: Cache<String, WrappingKey> = Caffeine.newBuilder()
        .expireAfterAccess(config.expireAfterAccessMins, TimeUnit.MINUTES)
        .maximumSize(config.maximumSize)
        .build()

    override fun getWrappingKey(alias: String): WrappingKey {
        val wrappingKeyInfo = store.findWrappingKey(alias)
            ?: throw IllegalStateException("The $alias is not created yet.")
        require(wrappingKeyInfo.encodingVersion == WRAPPING_KEY_ENCODING_VERSION) {
            "Unknown wrapping key encoding. Expected to be $WRAPPING_KEY_ENCODING_VERSION"
        }
        require(master.algorithm == wrappingKeyInfo.algorithmName) {
            "Expected algorithm is ${master.algorithm} but was ${wrappingKeyInfo.algorithmName}"
        }
        return cache.get(alias) { master.unwrapWrappingKey(wrappingKeyInfo.keyMaterial) }
    }

    override fun putWrappingKey(alias: String, wrappingKey: WrappingKey) {
        val wrappingKeyInfo = WrappingKeyInfo(
            encodingVersion = WRAPPING_KEY_ENCODING_VERSION,
            algorithmName =  wrappingKey.algorithm,
            keyMaterial = master.wrap(wrappingKey)
        )
        store.saveWrappingKey(alias, wrappingKeyInfo)
        cache.put(alias, wrappingKey)
    }

    override fun exists(alias: String): Boolean = store.findWrappingKey(alias) != null
}