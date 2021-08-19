package net.corda.crypto.testkit

import net.corda.crypto.impl.SigningServiceImpl

class MockSigningService internal constructor(
    val mocks: CryptoMocks,
    val cache: MockSigningKeyCache = mocks.signingKeyCache,
    val mockCryptoService: MockCryptoService = mocks.cryptoService(),
    defaultSignatureSchemeCodeName: String = mocks.defaultSignatureSchemeCodeName
) : SigningServiceImpl(
    cache = cache,
    cryptoService = mockCryptoService,
    schemeMetadata = mocks.schemeMetadata(),
    defaultSignatureSchemeCodeName = defaultSignatureSchemeCodeName
) {
    val signingKeyCache: MockSigningKeyCache = mocks.signingKeyCache
}