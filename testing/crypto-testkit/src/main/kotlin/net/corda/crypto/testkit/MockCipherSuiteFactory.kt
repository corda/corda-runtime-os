package net.corda.crypto.testkit

import net.corda.crypto.impl.DigestServiceProviderImpl
import net.corda.crypto.impl.SignatureVerificationServiceImpl
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService

class MockCipherSuiteFactory(
    private val mocks: CryptoMocks
) : CipherSuiteFactory {
    private val _signatureVerificationService: SignatureVerificationService by lazy {
        SignatureVerificationServiceImpl(mocks.schemeMetadata, getDigestService())
    }

    private val _digestService: DigestService by lazy {
        DigestServiceProviderImpl(null).getInstance(mocks.factories.cipherSuite)
    }

    override fun getSchemeMap(): CipherSchemeMetadata =
        mocks.schemeMetadata

    override fun getSignatureVerificationService(): SignatureVerificationService =
        _signatureVerificationService

    override fun getDigestService(): DigestService =
        _digestService
}