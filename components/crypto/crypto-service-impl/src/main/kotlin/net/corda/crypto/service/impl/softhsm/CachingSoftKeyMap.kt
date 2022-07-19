package net.corda.crypto.service.impl.softhsm

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.crypto.impl.config.CryptoSoftHSMConfig
import net.corda.crypto.service.softhsm.PrivateKeyMaterial
import net.corda.crypto.service.softhsm.SoftKeyMap
import net.corda.crypto.service.softhsm.SoftPrivateKeyWrapping
import net.corda.v5.cipher.suite.KeyMaterialSpec
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.concurrent.TimeUnit

class CachingSoftKeyMap(
    config: CryptoSoftHSMConfig.Cache,
    private val wrapping: SoftPrivateKeyWrapping
) : SoftKeyMap {
    private val cache: Cache<PublicKey, PrivateKey> = Caffeine.newBuilder()
        .expireAfterAccess(config.expireAfterAccessMins, TimeUnit.MINUTES)
        .maximumSize(config.maximumSize)
        .build()

    override fun getPrivateKey(publicKey: PublicKey, spec: KeyMaterialSpec): PrivateKey = cache.get(publicKey) {
        wrapping.unwrap(spec)
    }

    override fun wrapPrivateKey(keyPair: KeyPair, masterKeyAlias: String?): PrivateKeyMaterial {
        val privateKeyMaterial = wrapping.wrap(keyPair.private, masterKeyAlias)
        cache.put(keyPair.public, keyPair.private)
        return privateKeyMaterial
    }
}