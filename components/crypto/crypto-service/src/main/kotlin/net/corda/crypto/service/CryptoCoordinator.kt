package net.corda.crypto.service

import net.corda.crypto.service.rpc.CryptoRpcSub
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.impl.DefaultCryptoServiceProvider
import net.corda.crypto.impl.lifecycle.AbstractCryptoCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.v5.cipher.suite.CipherSuiteFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(immediate = true)
open class CryptoCoordinator @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(service = CryptoFactory::class)
    private val cryptoFactory: CryptoFactory,
    @Reference(service = DefaultCryptoServiceProvider::class)
    private val defaultCryptoServiceProvider: DefaultCryptoServiceProvider,
    @Reference(service = CryptoRpcSub::class)
    private val rpcSubs: List<CryptoRpcSub>
) : AbstractCryptoCoordinator(
    LifecycleCoordinatorName.forComponent<CryptoCoordinator>(),
    coordinatorFactory,
    configurationReadService,
    listOf(
        defaultCryptoServiceProvider,
        cipherSuiteFactory,
        cryptoFactory,
    ) + rpcSubs
)
