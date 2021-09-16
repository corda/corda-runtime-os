package net.corda.crypto.testkit

import net.corda.cipher.suite.impl.config.CryptoCacheConfig
import net.corda.components.crypto.services.persistence.DefaultCryptoCachedKeyInfo
import net.corda.components.crypto.services.persistence.DefaultCryptoPersistentKeyInfo
import net.corda.components.crypto.services.persistence.PersistentCache
import net.corda.components.crypto.services.persistence.PersistentCacheFactory
import net.corda.components.crypto.services.persistence.SigningPersistentKeyInfo

class MockPersistentCacheFactory : PersistentCacheFactory {
    override fun createSigningPersistentCache(
        config: CryptoCacheConfig
    ): PersistentCache<SigningPersistentKeyInfo, SigningPersistentKeyInfo> {
        return MockPersistentCache()
    }

    override fun createDefaultCryptoPersistentCache(
        config: CryptoCacheConfig
    ): PersistentCache<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo> {
        return MockPersistentCache()
    }
}