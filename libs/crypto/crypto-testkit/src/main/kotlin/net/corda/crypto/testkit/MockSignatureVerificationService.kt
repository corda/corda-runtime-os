package net.corda.crypto.testkit

import net.corda.cipher.suite.impl.SignatureVerificationServiceImpl

class MockSignatureVerificationService internal constructor(
    val mocks: CryptoMocks
) : SignatureVerificationServiceImpl(mocks.schemeMetadata(), mocks.digestService())