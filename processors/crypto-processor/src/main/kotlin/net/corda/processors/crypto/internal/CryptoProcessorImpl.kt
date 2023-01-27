package net.corda.processors.crypto.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.persistence.CryptoConnectionsFactory
import net.corda.crypto.persistence.HSMStore
import net.corda.crypto.persistence.SigningKeyStore
import net.corda.crypto.persistence.WrappingKeyStore
import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.crypto.service.CryptoFlowOpsBusService
import net.corda.crypto.service.CryptoOpsBusService
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.HSMRegistrationBusService
import net.corda.crypto.service.HSMService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.softhsm.SoftCryptoServiceProvider
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.orm.JpaEntitiesRegistry
import net.corda.processors.crypto.CryptoProcessor
import net.corda.schema.configuration.BootConfig.BOOT_CRYPTO
import net.corda.schema.configuration.BootConfig.BOOT_DB_PARAMS
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

@Suppress("LongParameterList")
@Component(service = [CryptoProcessor::class])
class CryptoProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = CryptoConnectionsFactory::class)
    private val cryptoConnectionsFactory: CryptoConnectionsFactory,
    @Reference(service = WrappingKeyStore::class)
    private val wrappingKeyStore: WrappingKeyStore,
    @Reference(service = SigningKeyStore::class)
    private val signingKeyStore: SigningKeyStore,
    @Reference(service = HSMStore::class)
    private val hsmStore: HSMStore,
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
    @Reference(service = HSMRegistrationBusService::class)
    private val hsmRegistration: HSMRegistrationBusService,
    @Reference(service = JpaEntitiesRegistry::class)
    private val entitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val vnodeInfo: VirtualNodeInfoReadService
) : CryptoProcessor {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    init {
        entitiesRegistry.register(CordaDb.Crypto.persistenceUnitName, CryptoEntities.classes)
        entitiesRegistry.register(CordaDb.CordaCluster.persistenceUnitName, ConfigurationEntities.classes)
    }

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::cryptoConnectionsFactory,
        ::wrappingKeyStore,
        ::signingKeyStore,
        ::hsmStore,
        ::signingServiceFactory,
        ::cryptoOspService,
        ::cryptoFlowOpsBusService,
        ::cryptoOpsClient,
        ::softCryptoServiceProvider,
        ::cryptoServiceFactory,
        ::hsmService,
        ::hsmRegistration,
        ::dbConnectionManager,
        ::vnodeInfo
    )

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<CryptoProcessor>(dependentComponents, ::eventHandler)

    @Volatile
    private var hsmAssociated: Boolean = false

    @Volatile
    private var dependenciesUp: Boolean = false

    private val tmpAssignmentFailureCounter = AtomicInteger(0)

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

    @Suppress("ComplexMethod", "NestedBlockDepth")
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Crypto processor received event $event.")
        when (event) {
            is StartEvent -> {
                // Nothing to do
            }
            is StopEvent -> {
                // Nothing to do
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    dependenciesUp = true
                    if (hsmAssociated) {
                        setStatus(event.status, coordinator)
                    } else {
                        coordinator.postEvent(AssociateHSM())
                    }
                } else {
                    dependenciesUp = false
                    setStatus(event.status, coordinator)
                }
            }
            is BootConfigEvent -> {
                logger.info("Crypto processor bootstrapping {}", configurationReadService::class.simpleName)
                configurationReadService.bootstrapConfig(event.config)

                logger.info("Crypto processor bootstrapping {}", dbConnectionManager::class.simpleName)
                dbConnectionManager.bootstrap(event.config.getConfig(BOOT_DB_PARAMS))

                logger.info("Crypto processor bootstrapping {}", cryptoServiceFactory::class.simpleName)
                cryptoServiceFactory.bootstrapConfig(event.config.getConfig(BOOT_CRYPTO))
            }
            is AssociateHSM -> {
                if(dependenciesUp) {
                    if (hsmAssociated) {
                        setStatus(LifecycleStatus.UP, coordinator)
                    } else {
                        logger.info("Assigning SOFT HSMs")
                        val failed = temporaryAssociateClusterWithSoftHSM()
                        if (failed.isNotEmpty()) {
                            if(tmpAssignmentFailureCounter.getAndIncrement() <= 5) {
                                logger.warn(
                                    "Failed to associate: [${failed.joinToString { "${it.first}:${it.second}" }}]" +
                                            ", will retry..."
                                )
                                coordinator.postEvent(AssociateHSM()) // try again
                            } else {
                                logger.error(
                                    "Failed to associate: [${failed.joinToString { "${it.first}:${it.second}" }}]")
                                setStatus(LifecycleStatus.ERROR, coordinator)
                            }
                        } else {
                            hsmAssociated = true
                            tmpAssignmentFailureCounter.set(0)
                            setStatus(LifecycleStatus.UP, coordinator)
                        }
                    }
                }
            }
        }
    }

    private fun setStatus(status: LifecycleStatus, coordinator: LifecycleCoordinator) {
        logger.info("Crypto processor is set to be $status")
        coordinator.updateStatus(status)
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
        hsmService.assignSoftHSM(tenantId, category)
        logger.info("Assigned SOFT HSM for $tenantId:$category")
        true
    } catch (e: Throwable) {
        logger.error("Failed to assign SOFT HSM for $tenantId:$category", e)
        false
    }

    class AssociateHSM : LifecycleEvent
}

