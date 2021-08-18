package net.corda.crypto.testkit

import net.corda.crypto.impl.SigningKeyCacheImpl
import net.corda.crypto.impl.SigningServicePersistentCacheFactory
import net.corda.cipher.suite.impl.dev.InMemorySigningServicePersistentCache

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