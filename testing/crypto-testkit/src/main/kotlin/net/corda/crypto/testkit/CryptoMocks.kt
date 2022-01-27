package net.corda.crypto.testkit

import net.corda.crypto.impl.CipherSchemeMetadataImpl
import net.corda.crypto.impl.DigestServiceImpl
import net.corda.crypto.impl.DoubleSHA256DigestFactory
import net.corda.crypto.impl.SignatureVerificationServiceImpl
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.DigestAlgorithmFactory
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService

class CryptoMocks : CipherSuiteFactory {

    val schemeMetadata: CipherSchemeMetadata by lazy(LazyThreadSafetyMode.PUBLICATION) {
        CipherSchemeMetadataImpl()
    }

    private val _verifier: SignatureVerificationService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        SignatureVerificationServiceImpl(
            schemeMetadata = schemeMetadata,
            hashingService = _digestService
        )
    }

    private val _digestService: DigestService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        DigestServiceImpl(
            schemeMetadata = schemeMetadata,
            customDigestAlgorithmFactories = mutableListOf<DigestAlgorithmFactory>(
                DoubleSHA256DigestFactory()
            ),
            customFactoriesProvider = null
        )
    }

    override fun getDigestService(): DigestService = _digestService

    override fun getSchemeMap(): CipherSchemeMetadata = schemeMetadata

    override fun getSignatureVerificationService(): SignatureVerificationService = _verifier
}