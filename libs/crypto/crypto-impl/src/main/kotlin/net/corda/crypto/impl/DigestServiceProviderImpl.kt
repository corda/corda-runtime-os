package net.corda.crypto.impl

import net.corda.crypto.DigestAlgorithmFactoryProvider
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.DigestAlgorithmFactory
import net.corda.v5.cipher.suite.DigestServiceProvider
import net.corda.v5.crypto.DigestService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality

@Component(service = [DigestServiceProvider::class])
class DigestServiceProviderImpl @Activate constructor(
    @Reference(service = DigestAlgorithmFactoryProvider::class, cardinality = ReferenceCardinality.OPTIONAL)
    private val customFactoriesProvider: DigestAlgorithmFactoryProvider?
) : DigestServiceProvider {
    companion object {
        const val SERVICE_NAME = "default"
    }

    override val name: String = SERVICE_NAME

    override fun getInstance(cipherSuiteFactory: CipherSuiteFactory): DigestService {
        val factories = mutableListOf<DigestAlgorithmFactory>(
            DoubleSHA256DigestFactory()
        )
        if(customFactoriesProvider != null) {
            factories.addAll(customFactoriesProvider.factories())
        }
        return DigestServiceImpl(
            schemeMetadata = cipherSuiteFactory.getSchemeMap(),
            customDigestAlgorithmFactories = factories
        )
    }
}