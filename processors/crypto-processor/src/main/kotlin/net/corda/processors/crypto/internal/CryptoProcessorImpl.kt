package net.corda.processors.crypto.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.HSMContext.NOT_FAIL_IF_ASSOCIATION_EXISTS
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.crypto.impl.config.createDefaultCryptoConfig
import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.crypto.persistence.hsm.HSMCacheProvider
import net.corda.crypto.persistence.signing.SigningKeyCacheProvider
import net.corda.crypto.persistence.soft.SoftCryptoKeyCacheProvider
import net.corda.crypto.service.CryptoFlowOpsBusService
import net.corda.crypto.service.CryptoOpsBusService
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.HSMConfigurationBusService
import net.corda.crypto.service.HSMRegistrationBusService
import net.corda.crypto.service.HSMService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.service.SoftCryptoServiceProvider
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
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
import net.corda.schema.configuration.BootConfig.BOOT_DB_PARAMS
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
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
    @Reference(service = CryptoOpsBusService::class)
    private val cryptoOspService: CryptoOpsBusService,
    @Reference(service = SoftCryptoServiceProvider::class)
    private val softCryptoServiceProvider: SoftCryptoServiceProvider,
    @Reference(service = CryptoFlowOpsBusService::class)
    private val cryptoFlowOpsBusService: CryptoFlowOpsBusService,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = CryptoServiceFactory::class)
    private val cryptoServiceFactory: CryptoServiceFactory,
    @Reference(service = HSMService::class)
    private val hsmService: HSMService,
    @Reference(service = HSMConfigurationBusService::class)
    private val hsmConfiguration: HSMConfigurationBusService,
    @Reference(service = HSMRegistrationBusService::class)
    private val hsmRegistration: HSMRegistrationBusService,
    @Reference(service = HSMCacheProvider::class)
    private val hsmCacheProvider: HSMCacheProvider,
    @Reference(service = JpaEntitiesRegistry::class)
    private val entitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = ConfigMerger::class)
    private val configMerger: ConfigMerger
) : CryptoProcessor {
    private companion object {
        const val CRYPTO_PROCESSOR_CLIENT_ID = "crypto.processor"
        val logger = contextLogger()
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
        ::cryptoFlowOpsBusService,
        ::cryptoOpsClient,
        ::softCryptoServiceProvider,
        ::cryptoServiceFactory,
        ::hsmService,
        ::hsmConfiguration,
        ::hsmRegistration,
        ::hsmCacheProvider,
        ::dbConnectionManager
    )

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start(bootConfig: SmartConfig) {
        logger.info("Crypto processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        logger.info("Crypto processor stopping.")
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Crypto processor received event $event.")
        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
            }
            is StopEvent -> {
                dependentComponents.stopAll()
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    logger.info("Assigning SOFT HSMs")
                    val failed = temporaryAssociateClusterWithSoftHSM()
                    if (failed.isNotEmpty()) {
                        logger.error("Failed to associate: [${failed.joinToString { "${it.first}:${it.second}" }}]")
                        coordinator.updateStatus(
                            LifecycleStatus.ERROR,
                            "Failed to associate SOFT HSMs with cluster tenants."
                        )
                        return
                    }
                }
                logger.info("Crypto processor is set to be ${event.status}")
                coordinator.updateStatus(event.status)
            }
            is BootConfigEvent -> {
                logger.info("Crypto  processor bootstrapping {}", configurationReadService::class.simpleName)
                configurationReadService.bootstrapConfig(event.config)

                logger.info("Crypto processor bootstrapping {}", dbConnectionManager::class.simpleName)
                dbConnectionManager.bootstrap(event.config.getConfig(BOOT_DB_PARAMS))

                publishCryptoBootstrapConfig(event)
            }
            else -> {
                logger.warn("Unexpected event $event!")
            }
        }
    }

    private fun publishCryptoBootstrapConfig(event: BootConfigEvent) {
        publisherFactory.createPublisher(
            PublisherConfig(CRYPTO_PROCESSOR_CLIENT_ID),
            configMerger.getMessagingConfig(event.config, null)
        ).use {
            it.start()
            val configValue = if (event.config.hasPath(CRYPTO_CONFIG)) {
                event.config.getConfig(CRYPTO_CONFIG)
            } else {
                event.config.factory.createDefaultCryptoConfig(
                    cryptoRootKey = KeyCredentials("root-passphrase", "root-salt"),
                    softKey = KeyCredentials("soft-passphrase", "soft-salt")
                )
            }.root().render()
            logger.info("Crypto Worker config\n: {}", configValue)
            val record = Record(CONFIG_TOPIC, CRYPTO_CONFIG, Configuration(configValue, "1", ConfigurationSchemaVersion(1, 0)))
            it.publish(listOf(record)).forEach { future -> future.get() }
        }
    }

    private fun temporaryAssociateClusterWithSoftHSM(): List<Pair<String, String>> {
        logger.info("Assigning SOFT HSM to cluster tenants.")
        val assigned = mutableListOf<Pair<String, String>>()
        val failed = mutableListOf<Pair<String, String>>()
        CryptoConsts.Categories.all.forEach { category ->
            CryptoTenants.allClusterTenants.forEach { tenantId ->
                if (tryAssignSoftHSM(tenantId, category)) {
                    assigned.add(Pair(tenantId, category))
                } else {
                    failed.add(Pair(tenantId, category))
                }
            }
        }
        logger.info(
            "SOFT HSM assignment is done. Assigned=[{}], failed=[{}]",
            assigned.joinToString { "${it.first}:${it.second}" },
            failed.joinToString { "${it.first}:${it.second}" }
        )
        return failed
    }

    private fun tryAssignSoftHSM(tenantId: String, category: String): Boolean = try {
        logger.info("Assigning SOFT HSM for $tenantId:$category")
        hsmService.assignSoftHSM(
            tenantId, category, mapOf(
                NOT_FAIL_IF_ASSOCIATION_EXISTS to "YES"
            )
        )
        logger.info("Assigned SOFT HSM for $tenantId:$category")
        true
    } catch (e: Throwable) {
        logger.error("Failed to assign SOFT HSM for $tenantId:$category", e)
        false
    }
}

