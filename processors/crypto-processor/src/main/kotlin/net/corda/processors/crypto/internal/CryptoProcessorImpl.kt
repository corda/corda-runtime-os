package net.corda.processors.crypto.internal

import net.corda.crypto.persistence.SigningKeyCacheProvider
import net.corda.crypto.persistence.SoftCryptoKeyCacheProvider
import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.crypto.service.CryptoFlowOpsService
import net.corda.crypto.service.CryptoOpsService
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.HSMRegistration
import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.service.SoftCryptoServiceProvider
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.orm.JpaEntitiesRegistry
import net.corda.processors.crypto.CryptoProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [CryptoProcessor::class])
class CryptoProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SoftCryptoKeyCacheProvider::class)
    private val softCryptoKeyCacheProvider: SoftCryptoKeyCacheProvider,
    @Reference(service = SigningKeyCacheProvider::class)
    private val signingKeyCacheProvider: SigningKeyCacheProvider,
    @Reference(service = SigningServiceFactory::class)
    private val signingServiceFactory: SigningServiceFactory,
    @Reference(service = CryptoOpsService::class)
    private val cryptoOspService: CryptoOpsService,
    @Reference(service = SoftCryptoServiceProvider::class)
    private val softCryptoServiceProviders: SoftCryptoServiceProvider,
    @Reference(service = CryptoFlowOpsService::class)
    private val cryptoFlowOpsService: CryptoFlowOpsService,
    @Reference(service = CryptoServiceFactory::class)
    private val cryptoServiceFactory: CryptoServiceFactory,
    @Reference(service = HSMRegistration::class)
    private val hsmRegistration: HSMRegistration,
    @Reference(service = JpaEntitiesRegistry::class)
    private val entitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager
) : CryptoProcessor {
    private companion object {
        val log = contextLogger()
    }

    init {
        // define the different DB Entity Sets
        //  entities can be in different packages, but all JPA classes must be passed in.
        entitiesRegistry.register(CordaDb.Crypto.persistenceUnitName, CryptoEntities.classes)
    }

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<CryptoProcessor>(::eventHandler)

    private val dependentComponents = DependentComponents.of(
        ::softCryptoKeyCacheProvider,
        ::signingKeyCacheProvider,
        ::signingServiceFactory,
        ::cryptoOspService,
        ::cryptoFlowOpsService,
        ::softCryptoServiceProviders,
        ::cryptoServiceFactory,
        ::hsmRegistration,
        ::dbConnectionManager
    )

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

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
        log.info("Crypto processor received event {}.", event)
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
                // intentional to avoid warning bellow
            }
            else -> {
                log.warn("Unexpected event $event!")
            }
        }
    }
}

