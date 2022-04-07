package net.corda.processors.db.internal

import net.corda.chunking.datamodel.ChunkingEntities
import net.corda.chunking.read.ChunkReadService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.write.ConfigWriteService
import net.corda.cpk.read.CpkReadService
import net.corda.cpk.write.CpkWriteService
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.connection.manager.DbAdmin
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.dbFallbackConfig
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntities
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
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
import net.corda.processors.db.DBProcessorException
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.write.db.VirtualNodeWriteService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource
import net.corda.schema.configuration.ConfigKeys.DB_CONFIG

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
    // TODO - remove this when DB migration is not needed anymore in this processor.
    @Reference(service = LiquibaseSchemaMigrator::class)
    private val schemaMigrator: LiquibaseSchemaMigrator,
    @Reference(service = DbAdmin::class)
    private val dbAdmin: DbAdmin,
    @Reference(service = ChunkReadService::class)
    private val chunkReadService: ChunkReadService,
    @Reference(service = CpkWriteService::class)
    private val cpkWriteService: CpkWriteService,
    @Reference(service = CpkReadService::class)
    private val cpkReadService: CpkReadService,
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
        ::cpkReadService
    )
    // keeping track of the DB Managers registration handler specifically because the bootstrap process needs to be split
    //  into 2 parts.
    private var dbManagerRegistrationHandler: RegistrationHandle? = null
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
                        bootstrapConfig!!.getInt(CONFIG_INSTANCE_ID),
                        dbConnectionManager.getClusterEntityManagerFactory())

                    configurationReadService.bootstrapConfig(bootstrapConfig!!)
                } else {
                    log.info("DB processor is ${event.status}")
                    coordinator.updateStatus(event.status)
                }
            }
            is BootConfigEvent -> {
                bootstrapConfig = event.config
                instanceId = event.config.getInt(CONFIG_INSTANCE_ID)

                log.info("Bootstrapping DB connection Manager")
                dbConnectionManager.bootstrap(event.config.getConfig(DB_CONFIG))
            }
            is StopEvent -> {
                dependentComponents.stopAll()
                dbManagerRegistrationHandler?.close()
                dbManagerRegistrationHandler = null
            }
            else -> {
                log.error("Unexpected event $event!")
            }
        }
    }

    /**
     * Uses the [dataSource] to apply the Liquibase schema migrations for each of the entities.
     *
     * @throws DBProcessorException If the cluster database cannot be connected to.
     */
    private fun migrateDatabase(dataSource: DataSource, dbChangeFiles: List<String>, controlTableSchema: String? = null) {
        val changeLogResourceFiles = setOf(DbSchema::class.java).mapTo(LinkedHashSet()) { klass ->
            ChangeLogResourceFiles(klass.packageName, dbChangeFiles, klass.classLoader)
        }
        val dbChange = ClassloaderChangeLog(changeLogResourceFiles)

        try {
            dataSource.connection.use { connection ->
                if(null == controlTableSchema)
                    schemaMigrator.updateDb(connection, dbChange)
                else
                    schemaMigrator.updateDb(connection, dbChange, controlTableSchema)
            }
        } catch (e: SQLException) {
            throw DBProcessorException("Could not connect to cluster database.", e)
        }
    }
}

data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent
