package net.corda.crypto.testkit

import net.corda.impl.caching.crypto.SimplePersistentCacheFactory
import net.corda.impl.cipher.suite.DefaultCachedKey
import net.corda.impl.cipher.suite.DefaultCryptoPersistentKey
import net.corda.impl.cipher.suite.DefaultKeyCacheImpl
import net.corda.impl.dev.cipher.suite.InMemorySimplePersistentCache

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