package net.corda.crypto.testkit

import net.corda.impl.cipher.suite.DefaultCryptoService
import net.corda.impl.cipher.suite.DefaultKeyCache
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import java.security.KeyPair

class MockCryptoService(
    val mocks: CryptoMocks,
    val cache: DefaultKeyCache = mocks.basicKeyCache,
) : DefaultCryptoService(
    cache = cache,
    schemeMetadata = mocks.schemeMetadata(),
    hashingService = mocks.digestService()
) {
    fun putKeyPair(alias: String, keyPair: KeyPair, scheme: SignatureScheme) =
        cache.save(alias, keyPair, scheme)
}