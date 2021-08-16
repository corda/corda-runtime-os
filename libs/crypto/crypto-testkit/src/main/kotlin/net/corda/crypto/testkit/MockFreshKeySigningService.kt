package net.corda.crypto.testkit

import net.corda.impl.crypto.FreshKeySigningServiceImpl

@Suppress("LongParameterList")
class MockFreshKeySigningService internal constructor(
    val mocks: CryptoMocks,
    val cache: MockSigningKeyCache = mocks.signingKeyCache,
    val cryptoService: MockCryptoService = mocks.cryptoService(),
    val freshKeysCryptoService: MockCryptoService = mocks.cryptoService(),
    defaultFreshKeySignatureSchemeCodeName: String = mocks.defaultFreshKeySignatureSchemeCodeName,
    val masterWrappingKeyAlias: String = "wrapping-key-alias"
) : FreshKeySigningServiceImpl(
    cache = cache,
    cryptoService = cryptoService,
    freshKeysCryptoService = freshKeysCryptoService,
    defaultFreshKeySignatureSchemeCodeName = defaultFreshKeySignatureSchemeCodeName,
    masterWrappingKeyAlias = masterWrappingKeyAlias,
    schemeMetadata = mocks.schemeMetadata()
) {
    val signingKeyCache: MockSigningKeyCache = mocks.signingKeyCache
}