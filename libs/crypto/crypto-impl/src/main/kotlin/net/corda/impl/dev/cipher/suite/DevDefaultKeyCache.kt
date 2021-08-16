package net.corda.impl.dev.cipher.suite

import net.corda.impl.caching.crypto.SimplePersistentCacheFactory
import net.corda.impl.cipher.suite.DefaultCachedKey
import net.corda.impl.cipher.suite.DefaultCryptoPersistentKey
import net.corda.impl.cipher.suite.DefaultKeyCacheImpl
import net.corda.v5.cipher.suite.CipherSchemeMetadata

class DevDefaultKeyCache (
        sandboxId: String,
        schemeMetadata: CipherSchemeMetadata,
        val cache: InMemorySimplePersistentCache<DefaultCachedKey, DefaultCryptoPersistentKey>
) : DefaultKeyCacheImpl(
        sandboxId = sandboxId,
        partition = null,
        passphrase = null,
        salt = null,
        cacheFactory = object : SimplePersistentCacheFactory<DefaultCachedKey, DefaultCryptoPersistentKey> {
            override fun create() =
                    cache
        },
        schemeMetadata = schemeMetadata
)