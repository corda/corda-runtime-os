package net.corda.cipher.suite.providers

import net.corda.crypto.impl.SignatureVerificationServiceImpl
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.SignatureVerificationServiceProvider
import net.corda.v5.crypto.SignatureVerificationService
import org.osgi.service.component.annotations.Component

@Component(service = [SignatureVerificationServiceProvider::class])
class SignatureVerificationServiceProviderImpl : SignatureVerificationServiceProvider {
    companion object {
        const val SERVICE_NAME = "default"
    }

    override val name: String = SERVICE_NAME

    override fun getInstance(cipherSuiteFactory: CipherSuiteFactory): SignatureVerificationService =
        SignatureVerificationServiceImpl(
            schemeMetadata = cipherSuiteFactory.getSchemeMap(),
            hashingService = cipherSuiteFactory.getDigestService()
        )
}