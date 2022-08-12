package net.corda.crypto.softhsm.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.crypto.softhsm.PrivateKeyMaterial
import net.corda.crypto.softhsm.SoftCacheConfig
import net.corda.crypto.softhsm.SoftKeyMap
import net.corda.crypto.softhsm.SoftPrivateKeyWrapping
import net.corda.v5.cipher.suite.KeyMaterialSpec
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.concurrent.TimeUnit

class CachingSoftKeyMap(
    config: SoftCacheConfig,
    private val wrapping: SoftPrivateKeyWrapping
) : SoftKeyMap {
    private val cache: Cache<PublicKey, PrivateKey> = CacheFactoryImpl().build(
        Caffeine.newBuilder()
            .expireAfterAccess(config.expireAfterAccessMins, TimeUnit.MINUTES)
            .maximumSize(config.maximumSize))

    override fun getPrivateKey(publicKey: PublicKey, spec: KeyMaterialSpec): PrivateKey = cache.get(publicKey) {
        wrapping.unwrap(spec)
    }

    override fun wrapPrivateKey(keyPair: KeyPair, masterKeyAlias: String?): PrivateKeyMaterial {
        val privateKeyMaterial = wrapping.wrap(keyPair.private, masterKeyAlias)
        cache.put(keyPair.public, keyPair.private)
        return privateKeyMaterial
    }
}