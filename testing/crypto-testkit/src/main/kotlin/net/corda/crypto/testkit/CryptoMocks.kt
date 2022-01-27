package net.corda.crypto.testkit

import net.corda.cipher.suite.providers.CipherSchemeMetadataProviderImpl
import net.corda.cipher.suite.providers.DigestServiceProviderImpl
import net.corda.cipher.suite.providers.SignatureVerificationServiceProviderImpl
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService

class CryptoMocks : CipherSuiteFactory {

    private val schemeMetadataProvider = CipherSchemeMetadataProviderImpl()
    private val verifierProvider = SignatureVerificationServiceProviderImpl()
    private val digestServiceProvider = DigestServiceProviderImpl()

    private val _schemeMap: CipherSchemeMetadata by lazy(LazyThreadSafetyMode.PUBLICATION) {
        schemeMetadataProvider.getInstance()
    }

    private val _verifier: SignatureVerificationService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        verifierProvider.getInstance(this)
    }

    private val _digestService: DigestService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        digestServiceProvider.getInstance(this)
    }

    val schemeMetadata: CipherSchemeMetadata = _schemeMap

    override fun getDigestService(): DigestService = _digestService

    override fun getSchemeMap(): CipherSchemeMetadata = _schemeMap

    override fun getSignatureVerificationService(): SignatureVerificationService = _verifier
}