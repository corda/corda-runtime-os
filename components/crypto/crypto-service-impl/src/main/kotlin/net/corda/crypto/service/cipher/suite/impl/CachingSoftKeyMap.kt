package net.corda.crypto.service.cipher.suite.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.crypto.service.cipher.suite.PrivateKeyMaterial
import net.corda.crypto.service.cipher.suite.SoftCacheConfig
import net.corda.crypto.service.cipher.suite.SoftKeyMap
import net.corda.crypto.service.cipher.suite.PrivateKeyWrapping
import net.corda.v5.cipher.suite.handlers.signing.KeyMaterialSpec
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.concurrent.TimeUnit

class CachingSoftKeyMap(
    config: SoftCacheConfig,
    private val wrapping: PrivateKeyWrapping
) : SoftKeyMap {
    private val cache: Cache<PublicKey, PrivateKey> = CacheFactoryImpl().build(
        Caffeine.newBuilder()
            .expireAfterAccess(config.expireAfterAccessMins, TimeUnit.MINUTES)
            .maximumSize(config.maximumSize))

    override fun unwrapPrivateKey(publicKey: PublicKey, spec: KeyMaterialSpec): PrivateKey = cache.get(publicKey) {
        wrapping.unwrap(spec)
    }

    override fun wrapPrivateKey(keyPair: KeyPair): PrivateKeyMaterial {
        val privateKeyMaterial = wrapping.wrap(keyPair.private)
        cache.put(keyPair.public, keyPair.private)
        return privateKeyMaterial
    }
}