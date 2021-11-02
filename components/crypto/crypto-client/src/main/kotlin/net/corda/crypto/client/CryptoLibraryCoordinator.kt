package net.corda.crypto.client

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.crypto.component.lifecycle.AbstractCryptoCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(immediate = true)
open class CryptoLibraryCoordinator @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(service = CryptoLibraryClientsFactoryProviderImpl::class)
    private val cryptoFactoryProvider: CryptoLibraryClientsFactoryProviderImpl
) : AbstractCryptoCoordinator(
    LifecycleCoordinatorName.forComponent<CryptoLibraryCoordinator>(),
    coordinatorFactory,
    configurationReadService,
    listOf(
        cipherSuiteFactory,
        cryptoFactoryProvider
    )
) {
    companion object {
        val devConfig by lazy(LazyThreadSafetyMode.PUBLICATION) {
            CryptoLibraryConfigImpl(
                mapOf(
                    "isDev" to "true"
                )
            )
        }
    }

    override fun handleEmptyCryptoConfig(): CryptoLibraryConfig = devConfig
}