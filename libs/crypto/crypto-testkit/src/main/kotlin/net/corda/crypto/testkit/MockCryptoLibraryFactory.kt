package net.corda.crypto.testkit

import net.corda.internal.crypto.CryptoLibraryFactory
import net.corda.internal.crypto.FreshKeySigningService
import net.corda.internal.crypto.SigningService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService

class MockCryptoLibraryFactory(
    val mocks: CryptoMocks,
) : CryptoLibraryFactory {
    override fun getFreshKeySigningService(
        passphrase: String,
        defaultSchemeCodeName: String,
        freshKeysDefaultSchemeCodeName: String
    ): FreshKeySigningService = mocks.freshKeySigningService(defaultSchemeCodeName)

    override fun getSigningService(
        category: String,
        passphrase: String,
        defaultSchemeCodeName: String
    ): SigningService = mocks.signingService(defaultSchemeCodeName)

    override fun getSignatureVerificationService(): SignatureVerificationService =
        mocks.signatureVerificationService()

    override fun getKeyEncodingService(): KeyEncodingService =
        mocks.schemeMetadata()

    override fun getCipherSchemeMetadata(): CipherSchemeMetadata =
        mocks.schemeMetadata()

    override fun getDigestService(): DigestService =
        mocks.digestService()
}