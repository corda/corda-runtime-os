package net.corda.crypto.testkit

import net.corda.crypto.impl.components.CipherSchemeMetadataImpl
import net.corda.crypto.impl.components.DigestServiceImpl
import net.corda.crypto.impl.components.SignatureVerificationServiceImpl
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService

class CryptoMocks {

    val schemeMetadata: CipherSchemeMetadata by lazy(LazyThreadSafetyMode.PUBLICATION) {
        CipherSchemeMetadataImpl()
    }

    val verifier: SignatureVerificationService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        SignatureVerificationServiceImpl(
            schemeMetadata = schemeMetadata,
            hashingService = digestService
        )
    }

    val digestService: DigestService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        DigestServiceImpl(
            schemeMetadata = schemeMetadata,
            customFactoriesProvider = null
        )
    }
}