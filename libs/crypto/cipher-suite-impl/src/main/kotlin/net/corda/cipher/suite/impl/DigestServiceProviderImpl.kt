package net.corda.cipher.suite.impl

import net.corda.crypto.DigestAlgorithmFactoryProvider
import net.corda.crypto.impl.DigestServiceImpl
import net.corda.crypto.impl.DoubleSHA256DigestFactory
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.DigestAlgorithmFactory
import net.corda.v5.cipher.suite.DigestServiceProvider
import net.corda.v5.crypto.DigestService
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [DigestServiceProvider::class])
class DigestServiceProviderImpl : DigestServiceProvider {
    companion object {
        const val SERVICE_NAME = "default"
    }

    @Volatile
    @Reference(service = DigestAlgorithmFactoryProvider::class)
    var customFactoriesProvider: DigestAlgorithmFactoryProvider? = null

    override val name: String = SERVICE_NAME

    override fun getInstance(cipherSuiteFactory: CipherSuiteFactory): DigestService {
        val factories = mutableListOf<DigestAlgorithmFactory>(
            DoubleSHA256DigestFactory()
        )
        return DigestServiceImpl(
            schemeMetadata = cipherSuiteFactory.getSchemeMap(),
            customDigestAlgorithmFactories = factories,
            customFactoriesProvider = customFactoriesProvider
        )
    }
}
