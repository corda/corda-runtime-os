package net.corda.processors.crypto.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.persistence.SigningKeyCacheProvider
import net.corda.crypto.persistence.SoftCryptoKeyCacheProvider
import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.crypto.service.CryptoFlowOpsService
import net.corda.crypto.service.CryptoOpsService
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.HSMRegistration
import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.service.SoftCryptoServiceProvider
import net.corda.data.config.Configuration
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.orm.JpaEntitiesRegistry
import net.corda.processors.crypto.CryptoProcessor
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.DB_CONFIG
import net.corda.v5.base.util.contextLogger
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
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
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
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
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
        const val CRYPTO_PROCESSOR_CLIENT_ID = "crypto.processor"
        val log = contextLogger()
    }

    init {
        // define the different DB Entity Sets
        //  entities can be in different packages, but all JPA classes must be passed in.
        entitiesRegistry.register(CordaDb.Crypto.persistenceUnitName, CryptoEntities.classes)

        entitiesRegistry.register(CordaDb.CordaCluster.persistenceUnitName, ConfigurationEntities.classes)
    }

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<CryptoProcessor>(::eventHandler)

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::softCryptoKeyCacheProvider,
        ::signingKeyCacheProvider,
        ::signingServiceFactory,
        ::cryptoOspService,
        ::cryptoFlowOpsService,
        ::cryptoOpsClient,
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
                log.info("Crypto  processor bootstrapping {}", configurationReadService::class.simpleName)
                configurationReadService.bootstrapConfig(event.config)

                log.info("Crypto processor bootstrapping {}", dbConnectionManager::class.simpleName)
                dbConnectionManager.bootstrap(event.config.getConfig(DB_CONFIG))

                val publisherConfig = PublisherConfig(CRYPTO_PROCESSOR_CLIENT_ID)
                val publisher = publisherFactory.createPublisher(publisherConfig, event.config)
                publisher.start()
                publisher.use {
                    val configValue = "{}"
                    val record = Record(CONFIG_TOPIC, CRYPTO_CONFIG, Configuration(configValue, "1"))
                    publisher.publish(listOf(record)).forEach { future -> future.get() }
                }
            }
            else -> {
                log.warn("Unexpected event $event!")
            }
        }
    }
}

