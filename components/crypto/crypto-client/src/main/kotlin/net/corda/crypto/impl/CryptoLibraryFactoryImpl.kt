package net.corda.crypto.impl

import net.corda.crypto.CryptoLibraryFactory
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CryptoLibraryFactory::class])
class CryptoLibraryFactoryImpl @Activate constructor(
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory
) : CryptoLibraryFactory {
    override fun getSignatureVerificationService(): SignatureVerificationService =
        cipherSuiteFactory.getSignatureVerificationService()

    override fun getKeyEncodingService(): KeyEncodingService =
        cipherSuiteFactory.getSchemeMap()

    override fun getCipherSchemeMetadata(): CipherSchemeMetadata =
        cipherSuiteFactory.getSchemeMap()

    override fun getDigestService(): DigestService =
        cipherSuiteFactory.getDigestService()
}