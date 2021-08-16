package net.corda.crypto.testkit

import net.corda.impl.crypto.SigningKeyCacheImpl
import net.corda.impl.crypto.SigningServicePersistentCacheFactory
import net.corda.impl.dev.cipher.suite.InMemorySigningServicePersistentCache

class MockSigningKeyCache internal constructor(
    val mocks: CryptoMocks,
    val cache: InMemorySigningServicePersistentCache
) : SigningKeyCacheImpl(
    sandboxId = mocks.sandboxId,
    keyEncoder = mocks.schemeMetadata(),
    object : SigningServicePersistentCacheFactory {
        override fun create() =
            cache
    }
)