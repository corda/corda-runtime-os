package net.corda.processors.db.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.write.ConfigWriteService
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
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
import net.corda.orm.JpaEntitiesRegistry
import net.corda.permissions.cache.PermissionCacheService
import net.corda.permissions.model.RpcRbacEntitiesSet
import net.corda.permissions.storage.reader.PermissionStorageReaderService
import net.corda.permissions.storage.writer.PermissionStorageWriterService
import net.corda.processors.db.DBProcessor
import net.corda.processors.db.DBProcessorException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.write.db.VirtualNodeWriteService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.sql.SQLException
import javax.sql.DataSource

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
    @Reference(service = PermissionCacheService::class)
    private val permissionCacheService: PermissionCacheService,
    @Reference(service = PermissionStorageReaderService::class)
    private val permissionStorageReaderService: PermissionStorageReaderService,
    @Reference(service = PermissionStorageWriterService::class)
    private val permissionStorageWriterService: PermissionStorageWriterService,
    @Reference(service = VirtualNodeWriteService::class)
    private val virtualNodeWriteService: VirtualNodeWriteService,
    // TODO: remove this when DB migration is not needed anymore in this processor.
    @Reference(service = DbConnectionsRepository::class)
    private val dbConnectionsRepository: DbConnectionsRepository,
    @Reference(service = LiquibaseSchemaMigrator::class)
    private val schemaMigrator: LiquibaseSchemaMigrator,
) : DBProcessor {
    init {
        // define the different DB Entity Sets
        //  entities can be in different packages, but all JPA classes must be passed in.
        // TODO - add VNode entities, for example.
        entitiesRegistry.register(CordaDb.CordaCluster.persistenceUnitName, ConfigurationEntities.classes)
        // TODO - refactor RpcRbacEntitiesSet
        entitiesRegistry.register(CordaDb.RBAC.persistenceUnitName, RpcRbacEntitiesSet().classes)
    }
    companion object {
        private val log = contextLogger()
    }

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<DBProcessorImpl>(::eventHandler)
    private val dependentComponents = DependentComponents.of(
        ::dbConnectionManager,
        ::configWriteService,
        ::configurationReadService,
        ::permissionCacheService,
        ::permissionStorageReaderService,
        ::permissionStorageWriterService,
        ::virtualNodeWriteService
    )

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
            }
            is RegistrationStatusChangeEvent -> {
                log.info("DB processor is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            is BootConfigEvent -> {

                log.info("Bootstrapping DB connection Manager")
                dbConnectionManager.bootstrap(event.config)

                // TODO - DB migration to be removed when part of cluster bootstrapping
                log.info("Running DB Migration")
                migrateDatabase(dbConnectionsRepository.clusterDataSource)

                val instanceId = event.config.getInt(CONFIG_INSTANCE_ID)
                log.info("Bootstrapping Config Write Service with instance ID: $instanceId")
                configWriteService.startProcessing(
                    event.config,
                    instanceId,
                    dbConnectionManager.clusterDbEntityManagerFactory)

                configurationReadService.bootstrapConfig(event.config)
            }
            is StopEvent -> {
                dependentComponents.stopAll()
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
    private fun migrateDatabase(dataSource: DataSource) {
        val dbChanges = listOf(
            "net/corda/db/schema/config/db.changelog-master.xml",
            "net/corda/db/schema/rbac/db.changelog-master.xml"
        ).map {
            ClassloaderChangeLog(setOf(DbSchema::class.java).mapTo(LinkedHashSet()) { klass ->
                ChangeLogResourceFiles(
                    klass.packageName,
                    listOf(it),
                    klass.classLoader
                )
            })
        }

        // Applying DB Changes independently as we cannot bundle them into a single change log
        // since there is a clash on the variables we use like `schema.name` in our Liquibase files
        // If it is defined once by one file and cannot be re-defined by the following.
        dbChanges.forEach { dbChange ->
            try {
                dataSource.connection.use { connection ->
                    schemaMigrator.updateDb(connection, dbChange)
                }
            } catch (e: SQLException) {
                throw DBProcessorException("Could not connect to cluster database.", e)
            }
        }
    }
}

data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent