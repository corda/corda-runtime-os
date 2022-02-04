package net.corda.processors.crypto.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.persistence.SigningKeysPersistenceProvider
import net.corda.crypto.persistence.SoftKeysPersistenceProvider
import net.corda.crypto.service.CryptoOpsService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.service.SoftCryptoServiceProvider
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.processors.crypto.CryptoProcessor
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

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
    @Reference(service = SoftCryptoServiceProvider::class)
    private val softCryptoServiceProviders: SoftCryptoServiceProvider
) : CryptoProcessor {
    private companion object {
        val log = contextLogger()
    }

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<CryptoProcessor>(::eventHandler)

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::softKeysPersistenceProvider,
        ::signingKeysPersistenceProvider,
        ::signingServiceFactory,
        ::cryptoOspService,
        ::softCryptoServiceProviders
    )

    override fun start(bootConfig: SmartConfig) {
        log.info("Crypto processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        log.info("Crypto processor stopping.")
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "Crypto processor received event $event." }
        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
            }
            is StopEvent -> {
                dependentComponents.stopAll()
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Crypto processor is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            is BootConfigEvent -> {
                configurationReadService.bootstrapConfig(event.config)
            }
            else -> {
                log.error("Unexpected event $event!")
            }
        }
    }
}

