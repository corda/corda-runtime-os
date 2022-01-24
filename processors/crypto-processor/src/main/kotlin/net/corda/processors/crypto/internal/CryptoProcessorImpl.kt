package net.corda.processors.crypto.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.persistence.SigningKeysPersistenceProvider
import net.corda.crypto.component.persistence.SoftKeysPersistenceProvider
import net.corda.crypto.impl.stopGracefully
import net.corda.crypto.service.CryptoOpsService
import net.corda.crypto.service.CryptoServiceProviderWithLifecycle
import net.corda.crypto.service.SigningServiceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.processors.crypto.CryptoProcessor
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicyOption

@Suppress("LongParameterList")
@Component(service = [CryptoProcessor::class])
class CryptoProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SoftKeysPersistenceProvider::class)
    private val softKeysPersistenceProvider: SoftKeysPersistenceProvider,
    @Reference(service = SigningKeysPersistenceProvider::class)
    private val signingKeysPersistenceProvider: SigningKeysPersistenceProvider,
    @Reference(service = SigningServiceFactory::class)
    private val signingServiceFactory: SigningServiceFactory,
    @Reference(service = CryptoOpsService::class)
    private val cryptoOspService: CryptoOpsService,
    @Reference(
        service = CryptoServiceProviderWithLifecycle::class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policyOption = ReferencePolicyOption.GREEDY
    )
    private val cryptoServiceProviders: List<CryptoServiceProviderWithLifecycle>
) : CryptoProcessor {
    private companion object {
        val log = contextLogger()
    }

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<CryptoProcessorImpl>(::eventHandler)

    override fun start(bootConfig: SmartConfig) {
        log.info("Crypto processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        log.info("Crypto processor stopping.")
        lifecycleCoordinator.stop()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "Crypto processor received event $event." }
        when (event) {
            is StartEvent -> {
                startDependencies()
            }
            is StopEvent -> {
                stopDependencies()
            }
            is BootConfigEvent -> {
                configurationReadService.bootstrapConfig(event.config)
            }
            else -> {
                log.error("Unexpected event $event!")
            }
        }
    }

    private fun startDependencies() {
        configurationReadService.start()
        softKeysPersistenceProvider.start()
        signingKeysPersistenceProvider.start()
        signingServiceFactory.start()
        cryptoOspService.start()
        cryptoServiceProviders.forEach {
            it.start()
        }
    }

    private fun stopDependencies() {
        cryptoServiceProviders.forEach {
            it.stopGracefully()
        }
        cryptoOspService.stopGracefully()
        signingServiceFactory.stopGracefully()
        signingKeysPersistenceProvider.stopGracefully()
        softKeysPersistenceProvider.stopGracefully()
        configurationReadService.stopGracefully()
    }
}

