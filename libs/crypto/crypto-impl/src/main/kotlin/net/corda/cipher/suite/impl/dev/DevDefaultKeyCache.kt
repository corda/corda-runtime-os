package net.corda.cipher.suite.impl.dev

import net.corda.crypto.impl.caching.SimplePersistentCacheFactory
import net.corda.cipher.suite.impl.DefaultCachedKey
import net.corda.cipher.suite.impl.DefaultKeyCacheImpl
import net.corda.v5.cipher.suite.CipherSchemeMetadata

class DevDefaultKeyCache(
    sandboxId: String,
    schemeMetadata: CipherSchemeMetadata,
    val cache: InMemorySimplePersistentCache<DefaultCachedKey, Any>
) : DefaultKeyCacheImpl(
    sandboxId = sandboxId,
    partition = null,
    passphrase = null,
    salt = null,
    cacheFactory = object : SimplePersistentCacheFactory<DefaultCachedKey, Any> {
        override fun create() =
            cache
    },
    schemeMetadata = schemeMetadata
)