package net.corda.crypto.impl.persistence

import net.corda.crypto.impl.config.CryptoCacheConfig

/**
 * Defines a factory which must create a new instance implementing [PersistentCache]
 */
@FunctionalInterface
interface PersistentCacheFactory {
    fun createSigningPersistentCache(
        config: CryptoCacheConfig
    ): PersistentCache<SigningPersistentKeyInfo, SigningPersistentKeyInfo>
    fun createDefaultCryptoPersistentCache(
        config: CryptoCacheConfig
    ): PersistentCache<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo>
}

