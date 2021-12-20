package net.corda.crypto.testkit

import net.corda.crypto.CryptoLibraryFactory
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService

class MockCryptoLibraryFactory(
    private val mocks: CryptoMocks
) : CryptoLibraryFactory {
    override fun getSignatureVerificationService(): SignatureVerificationService =
        mocks.factories.cipherSuite.getSignatureVerificationService()

    override fun getKeyEncodingService(): KeyEncodingService =
        mocks.factories.cipherSuite.getSchemeMap()

    override fun getCipherSchemeMetadata(): CipherSchemeMetadata =
        mocks.factories.cipherSuite.getSchemeMap()

    override fun getDigestService(): DigestService =
        mocks.factories.cipherSuite.getDigestService()
}