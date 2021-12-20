package net.corda.crypto.impl

import net.corda.crypto.DigestAlgorithmFactoryProvider
import net.corda.crypto.DigestAlgorithmFactoryProviderRegistry
import net.corda.v5.cipher.suite.DigestAlgorithmFactory
import org.osgi.service.component.annotations.Component

/**
 * Provide a map of [DigestAlgorithmFactory] instances.  We just defer the [factories()] call to the provider set
 * by the [register] method, or return an [emptyMap].
 */
@Component(service = [DigestAlgorithmFactoryProvider::class, DigestAlgorithmFactoryProviderRegistry::class])
class DigestAlgorithmFactoryProviderImpl : DigestAlgorithmFactoryProvider, DigestAlgorithmFactoryProviderRegistry {
    // In order for OSGi to resolve and create all the services in crypto-impl we need to ensure that there is
    // an implementation for each service interface.  Either in this bundle *or* in something that is loaded
    // by the OSGi framework and started before this bundle.
    //
    // It is (conceptually) simpler for a caller to get a [@Reference] to this implementation, then register their provider.

    private var provider : () -> Map<String, DigestAlgorithmFactory> = ::emptyMap

    override fun get(algorithmName: String): DigestAlgorithmFactory? = provider()[algorithmName]

    override fun register(factoryProvider: () -> Map<String, DigestAlgorithmFactory>) { provider = factoryProvider }

    override fun unregister() { provider = ::emptyMap }
}
