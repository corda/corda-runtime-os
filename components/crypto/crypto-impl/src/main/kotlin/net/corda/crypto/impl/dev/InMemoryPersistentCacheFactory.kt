package net.corda.crypto.impl.dev

import net.corda.crypto.impl.config.CryptoCacheConfig
import net.corda.crypto.impl.persistence.DefaultCryptoCachedKeyInfo
import net.corda.crypto.impl.persistence.DefaultCryptoPersistentKeyInfo
import net.corda.crypto.impl.persistence.PersistentCache
import net.corda.crypto.impl.persistence.PersistentCacheFactory
import net.corda.crypto.impl.persistence.SigningPersistentKeyInfo

class InMemoryPersistentCacheFactory : PersistentCacheFactory {
    override fun createSigningPersistentCache(
        config: CryptoCacheConfig
    ): PersistentCache<SigningPersistentKeyInfo, SigningPersistentKeyInfo> {
        return InMemoryPersistentCache()
    }

    override fun createDefaultCryptoPersistentCache(
        config: CryptoCacheConfig
    ): PersistentCache<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo> {
        return InMemoryPersistentCache()
    }
}