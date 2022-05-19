package net.corda.processors.db.internal

import net.corda.chunking.datamodel.ChunkingEntities
import net.corda.chunking.read.ChunkReadService
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.write.ConfigWriteService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.cpk.read.CpkReadService
import net.corda.cpk.write.CpkWriteService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.entityprocessor.FlowPersistenceService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntities
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.orm.JpaEntitiesRegistry
import net.corda.permissions.model.RbacEntities
import net.corda.permissions.storage.reader.PermissionStorageReaderService
import net.corda.permissions.storage.writer.PermissionStorageWriterService
import net.corda.processors.db.DBProcessor
import net.corda.processors.db.internal.reconcile.db.CpiInfoDbReader
import net.corda.reconciliation.Reconciler
import net.corda.reconciliation.ReconcilerFactory
import net.corda.schema.configuration.BootConfig.BOOT_DB_PARAMS
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_CPI_INFO_INTERVAL_MS
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.write.db.VirtualNodeWriteService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("Unused", "LongParameterList")
@Component(service = [DBProcessor::class])
class DBProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val entitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = ConfigWriteService::class)
    private val configWriteService: ConfigWriteService,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = PermissionStorageReaderService::class)
    private val permissionStorageReaderService: PermissionStorageReaderService,
    @Reference(service = PermissionStorageWriterService::class)
    private val permissionStorageWriterService: PermissionStorageWriterService,
    @Reference(service = VirtualNodeWriteService::class)
    private val virtualNodeWriteService: VirtualNodeWriteService,
    @Reference(service = ChunkReadService::class)
    private val chunkReadService: ChunkReadService,
    @Reference(service = CpkWriteService::class)
    private val cpkWriteService: CpkWriteService,
    @Reference(service = CpkReadService::class)
    private val cpkReadService: CpkReadService,
    @Reference(service = FlowPersistenceService::class)
    private val flowPersistenceService: FlowPersistenceService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = CpiInfoWriteService::class)
    private val cpiInfoWriteService: CpiInfoWriteService,
    @Reference(service = ReconcilerFactory::class)
    private val reconcilerFactory: ReconcilerFactory
) : DBProcessor {
    init {
        // define the different DB Entity Sets
        //  entities can be in different packages, but all JPA classes must be passed in.
        entitiesRegistry.register(
            CordaDb.CordaCluster.persistenceUnitName,
            ConfigurationEntities.classes
                    + VirtualNodeEntities.classes
                    + ChunkingEntities.classes
                    + CpiEntities.classes
        )
        entitiesRegistry.register(CordaDb.RBAC.persistenceUnitName, RbacEntities.classes)
    }
    companion object {
        private val log = contextLogger()
    }

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<DBProcessorImpl>(::eventHandler)
    private val dependentComponents = DependentComponents.of(
        ::dbConnectionManager,
        ::configWriteService,
        ::configurationReadService,
        ::permissionStorageReaderService,
        ::permissionStorageWriterService,
        ::virtualNodeWriteService,
        ::chunkReadService,
        ::cpkWriteService,
        ::cpkReadService,
        ::flowPersistenceService,
        ::cpkReadService,
        ::cpiInfoReadService,
        ::cpiInfoWriteService
    )

    private var cpiInfoDbReader: CpiInfoDbReader? = null
    private var cpiInfoReconciler: Reconciler? = null

    // keeping track of the DB Managers registration handler specifically because the bootstrap process needs to be split
    //  into 2 parts.
    private var dbManagerRegistrationHandler: RegistrationHandle? = null
    private var configSubscription: AutoCloseable? = null
    private var bootstrapConfig: SmartConfig? = null
    private var instanceId: Int? = null

    override fun start(bootConfig: SmartConfig) {
        log.info("DB processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        log.info("DB processor stopping.")
        lifecycleCoordinator.stop()
    }

    @Suppress("ComplexMethod", "NestedBlockDepth")
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "DB processor received event $event." }

        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
                dbManagerRegistrationHandler = lifecycleCoordinator.followStatusChangesByName(
                    setOf(LifecycleCoordinatorName.forComponent<DbConnectionManager>()))
            }
            is RegistrationStatusChangeEvent -> {
                if (event.registration == dbManagerRegistrationHandler) {
                    log.info("DB Connection Manager has been initialised")

                    // ready to continue bootstrapping processor
                    log.info("Bootstrapping Config Write Service with instance ID: $instanceId")
                    configWriteService.startProcessing(
                        bootstrapConfig!!,
                        dbConnectionManager.getClusterEntityManagerFactory())

                    configurationReadService.bootstrapConfig(bootstrapConfig!!)
                } else {
                    log.info("DB processor is ${event.status}")
                    if (event.status == LifecycleStatus.UP) {
                        configSubscription = configurationReadService.registerComponentForUpdates(
                            coordinator, setOf(
                                ConfigKeys.RECONCILIATION_CONFIG
                            )
                        )
                    }
                    coordinator.updateStatus(event.status)
                }
            }
            is ConfigChangedEvent -> {
                event.config[ConfigKeys.RECONCILIATION_CONFIG]?.getLong(RECONCILIATION_CPI_INFO_INTERVAL_MS)
                    ?.let { cpiInfoReconciliationIntervalMs ->
                        log.info("Cpi info reconciliation interval set to $cpiInfoReconciliationIntervalMs ms")
                        createOrUpdateCpiInfoReconciler(cpiInfoReconciliationIntervalMs)
                    }
            }
            is BootConfigEvent -> {
                bootstrapConfig = event.config
                instanceId = event.config.getInt(INSTANCE_ID)

                log.info("Bootstrapping DB connection Manager")
                dbConnectionManager.bootstrap(event.config.getConfig(BOOT_DB_PARAMS))
            }
            is StopEvent -> {
                dependentComponents.stopAll()
                cpiInfoReconciler?.close()
                cpiInfoReconciler = null
                cpiInfoDbReader?.close()
                cpiInfoDbReader = null
                dbManagerRegistrationHandler?.close()
                dbManagerRegistrationHandler = null
            }
            else -> {
                log.error("Unexpected event $event!")
            }
        }
    }

    private fun createOrUpdateCpiInfoReconciler(cpiInfoReconciliationIntervalMs: Long) {
        if (cpiInfoDbReader == null) {
            log.info("Creating ${CpiInfoDbReader::class.java.name}")
            cpiInfoDbReader =
                CpiInfoDbReader(coordinatorFactory, dbConnectionManager).also { it.start() }
        }

        if (cpiInfoReconciler == null) {
            cpiInfoReconciler = reconcilerFactory.create(
                dbReader = cpiInfoDbReader!!,
                kafkaReader = cpiInfoReadService,
                writer = cpiInfoWriteService,
                keyClass = CpiIdentifier::class.java,
                valueClass = CpiMetadata::class.java,
                reconciliationIntervalMs = cpiInfoReconciliationIntervalMs
            ).also { it.start() }
        } else {
            log.info("Updating Cpi Info ${Reconciler::class.java.name}")
            cpiInfoReconciler!!.updateInterval(cpiInfoReconciliationIntervalMs)
        }
    }
}

data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent
