package net.corda.crypto.impl

import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.DigestServiceProvider
import net.corda.v5.crypto.DigestService
import org.osgi.service.component.annotations.Component

@Component
class DigestServiceProviderImpl : DigestServiceProvider {
    companion object {
        const val SERVICE_NAME = "default"
    }

    override val name: String = SERVICE_NAME

    override fun getInstance(cipherSuiteFactory: CipherSuiteFactory): DigestService = DigestServiceImpl(
        schemeMetadata = cipherSuiteFactory.getSchemeMap(),
        customDigestAlgorithmFactories = listOf(
            DoubleSHA256DigestFactory()
        )
    )
}