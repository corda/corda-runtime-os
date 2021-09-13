package net.corda.crypto.impl

import net.corda.crypto.CryptoLibraryFactory
import net.corda.crypto.FreshKeySigningService
import net.corda.crypto.SigningService
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import org.osgi.service.component.annotations.Activate

class CryptoLibraryFactoryImpl @Activate constructor(
    private val memberId: String,
    private val requestingComponent: String,
    private val cipherSuiteFactory: CipherSuiteFactory,
    private val publisherFactory: PublisherFactory,
) : CryptoLibraryFactory {
    override fun getSignatureVerificationService(): SignatureVerificationService =
        cipherSuiteFactory.getSignatureVerificationService()

    override fun getKeyEncodingService(): KeyEncodingService =
        cipherSuiteFactory.getSchemeMap()

    override fun getCipherSchemeMetadata(): CipherSchemeMetadata =
        cipherSuiteFactory.getSchemeMap()

    override fun getDigestService(): DigestService =
        cipherSuiteFactory.getDigestService()

    override fun getFreshKeySigningService(): FreshKeySigningService {
        TODO("Not yet implemented")
    }

    override fun getSigningService(category: String): SigningService {
        TODO("Not yet implemented")
    }
}