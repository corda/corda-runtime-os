package net.corda.crypto.testkit

import net.corda.v5.cipher.suite.CipherSchemeMetadata

class MockCipherSchemeMetadata(
        val mocks: CryptoMocks,
        private val impl: CipherSchemeMetadata
) : CipherSchemeMetadata by impl