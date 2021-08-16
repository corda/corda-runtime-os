package net.corda.crypto.testkit

import net.corda.impl.cipher.suite.SignatureVerificationServiceImpl

class MockSignatureVerificationService internal constructor(
        val mocks: CryptoMocks
) : SignatureVerificationServiceImpl(mocks.schemeMetadata(), mocks.digestService())