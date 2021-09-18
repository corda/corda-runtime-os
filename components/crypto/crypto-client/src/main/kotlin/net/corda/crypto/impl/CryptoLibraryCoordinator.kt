package net.corda.crypto.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.CryptoLibraryFactoryProvider
import net.corda.crypto.impl.lifecycle.AbstractCryptoCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.v5.cipher.suite.CipherSuiteFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(immediate = true)
class CryptoLibraryCoordinator @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(service = CryptoLibraryFactoryProvider::class)
    private val cryptoFactoryProvider: CryptoLibraryFactoryProvider
) : AbstractCryptoCoordinator(
    coordinatorFactory,
    configurationReadService,
    listOf(
        cipherSuiteFactory,
        cryptoFactoryProvider
    )
)