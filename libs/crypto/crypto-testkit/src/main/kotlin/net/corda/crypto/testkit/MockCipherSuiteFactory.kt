package net.corda.crypto.testkit

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.config.CryptoServiceConfigInfo
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService

class MockCipherSuiteFactory(
        val mocks: CryptoMocks
) : CipherSuiteFactory {
    override fun getSchemeMap(): CipherSchemeMetadata = mocks.schemeMetadata()

    override fun getCryptoService(info: CryptoServiceConfigInfo): CryptoService = mocks.cryptoService()

    override fun getSignatureVerificationService(): SignatureVerificationService = mocks.signatureVerificationService()

    override fun getDigestService(): DigestService = mocks.digestService()
}