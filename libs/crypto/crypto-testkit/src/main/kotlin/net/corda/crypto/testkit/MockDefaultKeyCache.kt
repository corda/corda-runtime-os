package net.corda.crypto.testkit

import net.corda.crypto.impl.caching.SimplePersistentCacheFactory
import net.corda.cipher.suite.impl.DefaultCachedKey
import net.corda.cipher.suite.impl.DefaultKeyCacheImpl
import net.corda.cipher.suite.impl.dev.InMemorySimplePersistentCache

class MockDefaultKeyCache internal constructor(
    val mocks: CryptoMocks,
    val cache: InMemorySimplePersistentCache<DefaultCachedKey, DefaultCryptoPersistentKey>
) : DefaultKeyCacheImpl(
    sandboxId = mocks.sandboxId,
    partition = null,
    passphrase = null,
    salt = null,
    cacheFactory = object : SimplePersistentCacheFactory<DefaultCachedKey, DefaultCryptoPersistentKey> {
        override fun create() =
            cache
    },
    schemeMetadata = mocks.schemeMetadata()
)