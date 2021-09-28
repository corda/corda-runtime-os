package net.corda.crypto.impl.dev

import net.corda.crypto.impl.config.CryptoCacheConfig
import net.corda.crypto.impl.persistence.DefaultCryptoCachedKeyInfo
import net.corda.crypto.impl.persistence.DefaultCryptoPersistentKeyInfo
import net.corda.crypto.impl.persistence.PersistentCache
import net.corda.crypto.impl.persistence.PersistentCacheFactory
import net.corda.crypto.impl.persistence.SigningPersistentKeyInfo
import org.osgi.service.component.annotations.Component

@Component(service = [PersistentCacheFactory::class])
class InMemoryPersistentCacheFactory : PersistentCacheFactory {
    companion object {
        const val NAME = "dev"
    }

    override val name: String = NAME

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