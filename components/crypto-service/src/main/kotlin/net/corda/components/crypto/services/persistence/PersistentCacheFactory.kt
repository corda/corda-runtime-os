package net.corda.components.crypto.services.persistence

import net.corda.components.crypto.config.CryptoCacheConfig

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

