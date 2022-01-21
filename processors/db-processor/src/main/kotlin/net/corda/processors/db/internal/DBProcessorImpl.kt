package net.corda.processors.db.internal

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.write.ConfigWriteService
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.core.HikariDataSourceFactory
import net.corda.db.schema.DbSchema
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.datamodel.ConfigAuditEntity
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.permissions.cache.PermissionCacheService
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
    @Reference(service = ConfigWriteService::class)
    private val configWriteService: ConfigWriteService,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
    @Reference(service = LiquibaseSchemaMigrator::class)
    private val schemaMigrator: LiquibaseSchemaMigrator,
    @Reference(service = PermissionCacheService::class)
    private val permissionCacheService: PermissionCacheService,
    @Reference(service = PermissionStorageReaderService::class)
    private val permissionStorageReaderService: PermissionStorageReaderService,
    @Reference(service = PermissionStorageWriterService::class)
    private val permissionStorageWriterService: PermissionStorageWriterService,
    @Reference(service = VirtualNodeWriteService::class)
    private val virtualNodeWriteService: VirtualNodeWriteService
) : DBProcessor {

    companion object {
        private val log = contextLogger()
    }

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<DBProcessorImpl>(::eventHandler)

    override fun start(bootConfig: SmartConfig) {
        log.info("DB processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        log.info("DB processor stopping.")
        lifecycleCoordinator.stop()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "DB processor received event $event." }
        when (event) {
            is StartEvent -> {
                configWriteService.start()
                configurationReadService.start()
                permissionCacheService.start()
                permissionStorageReaderService.start()
                permissionStorageWriterService.start()
                virtualNodeWriteService.start()
            }
            is BootConfigEvent -> {
                val dataSource = createDataSource(event.config)
                checkDatabaseConnection(dataSource)
                migrateDatabase(dataSource)

                val instanceId = event.config.getInt(CONFIG_INSTANCE_ID)
                val entityManagerFactory = createEntityManagerFactory(dataSource)
                configWriteService.startProcessing(event.config, instanceId, entityManagerFactory)
                // TODO - Joel - Reconsider this. Should this information come from the config reader?
                virtualNodeWriteService.startProcessing(event.config, instanceId, entityManagerFactory)

                configurationReadService.bootstrapConfig(event.config)
            }
            is StopEvent -> {
                permissionStorageWriterService.stop()
                permissionStorageReaderService.stop()
                permissionCacheService.stop()
                configWriteService.stop()
                configurationReadService.stop()
                virtualNodeWriteService.stop()
            }
            else -> {
                log.error("Unexpected event $event!")
            }
        }
    }

    /**
     * Checks that it is possible to connect to the cluster database using the [dataSource].
     *
     * @throws DBProcessorException If the cluster database cannot be connected to.
     */
    private fun checkDatabaseConnection(dataSource: DataSource) = try {
        dataSource.connection.close()
    } catch (e: Exception) {
        throw DBProcessorException("Could not connect to cluster database.", e)
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

    /** Creates an `EntityManagerFactory` using the [dataSource]. */
    private fun createEntityManagerFactory(dataSource: DataSource) = entityManagerFactoryFactory.create(
        PERSISTENCE_UNIT_NAME,
        listOf(ConfigEntity::class.java, ConfigAuditEntity::class.java),
        DbEntityManagerConfiguration(dataSource)
    )

    /** Creates a [DataSource] using the [config]. */
    private fun createDataSource(config: SmartConfig): DataSource {
        val fallbackConfig = ConfigFactory.empty()
            .withValue(CONFIG_DB_DRIVER, ConfigValueFactory.fromAnyRef(CONFIG_DB_DRIVER_DEFAULT))
            .withValue(CONFIG_JDBC_URL, ConfigValueFactory.fromAnyRef(CONFIG_JDBC_URL_DEFAULT))
            .withValue(CONFIG_MAX_POOL_SIZE, ConfigValueFactory.fromAnyRef(CONFIG_MAX_POOL_SIZE_DEFAULT))
        val configWithFallback = config.withFallback(fallbackConfig)

        val driver = configWithFallback.getString(CONFIG_DB_DRIVER)
        val jdbcUrl = configWithFallback.getString(CONFIG_JDBC_URL)
        val maxPoolSize = configWithFallback.getInt(CONFIG_MAX_POOL_SIZE)
        
        val username = getConfigStringOrNull(config, CONFIG_DB_USER) ?: throw DBProcessorException(
            "No username provided to connect to cluster database. Pass the `-d cluster.user` flag at worker startup." +
                    "Provided config: ${config.root().render()}"
        )
        val password = getConfigStringOrNull(config, CONFIG_DB_PASS) ?: throw DBProcessorException(
            "No password provided to connect to cluster database. Pass the `-d cluster.pass` flag at worker startup." +
                    "Provided config: ${config.root().render()}"
        )

        return HikariDataSourceFactory().create(driver, jdbcUrl, username, password, false, maxPoolSize)
    }

    /** Returns the string at [path] from [config], or null if the path doesn't exist. */
    private fun getConfigStringOrNull(config: SmartConfig, path: String) =
        if (config.hasPath(path)) config.getString(path) else null
}

data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent