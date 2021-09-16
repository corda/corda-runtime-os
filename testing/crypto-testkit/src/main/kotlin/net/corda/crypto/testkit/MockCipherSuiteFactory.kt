package net.corda.crypto.testkit

import net.corda.cipher.suite.impl.DigestServiceProviderImpl
import net.corda.cipher.suite.impl.SignatureVerificationServiceImpl
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.config.CryptoServiceConfigInfo
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService

class MockCipherSuiteFactory(
    private val mocks: CryptoMocks
) : CipherSuiteFactory {
    private val _signatureVerificationService: SignatureVerificationService by lazy {
        SignatureVerificationServiceImpl(mocks.schemeMetadata, getDigestService())
    }

    private val _digestService: DigestService by lazy {
        DigestServiceProviderImpl().getInstance(mocks.factories.cipherSuite)
    }

    override fun getSchemeMap(): CipherSchemeMetadata =
        mocks.schemeMetadata

    override fun getCryptoService(info: CryptoServiceConfigInfo): CryptoService {
        throw NotImplementedError()
    }

    override fun getSignatureVerificationService(): SignatureVerificationService =
        _signatureVerificationService

    override fun getDigestService(): DigestService =
        _digestService
}