package net.corda.processors.db.internal

import com.typesafe.config.Config
import net.corda.configuration.rpcops.ConfigRPCOpsService
import net.corda.configuration.write.ConfigWriteService
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.core.HikariDataSourceFactory
import net.corda.db.schema.DbSchema
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.datamodel.ConfigAuditEntity
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.processors.db.DBProcessor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.sql.DataSource

/** The processor for a `DBWorker`. */
@Suppress("Unused")
@Component(service = [DBProcessor::class])
class DBProcessorImpl @Activate constructor(
    @Reference(service = ConfigWriteService::class)
    private val configWriteService: ConfigWriteService,
    @Reference(service = ConfigRPCOpsService::class)
    private val configRPCOpsService: ConfigRPCOpsService,
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
    @Reference(service = LiquibaseSchemaMigrator::class)
    private val schemaMigrator: LiquibaseSchemaMigrator
) : DBProcessor {

    override fun start(config: SmartConfig) {
        val dataSource = createDataSource(config)
        checkDatabaseConnection(dataSource)
        migrateDatabase(dataSource)

        configWriteService.start()
        val instanceId = config.getInt(CONFIG_INSTANCE_ID)
        val entityManagerFactory = createEntityManagerFactory(dataSource)
        configWriteService.startProcessing(config, instanceId, entityManagerFactory)

        configRPCOpsService.start()
        configRPCOpsService.startProcessing(config)
    }

    override fun stop() {
        configWriteService.stop()
    }

    /**
     * Checks that it is possible to connect to the cluster database using the [dataSource].
     *
     * @throws `SQLException` If the cluster database cannot be connected to.
     */
    private fun checkDatabaseConnection(dataSource: DataSource) = dataSource.connection.close()

    /** Uses the [dataSource] to apply the Liquibase schema migrations for each of the entities. */
    private fun migrateDatabase(dataSource: DataSource) {
        val changeLogResourceFiles = setOf(DbSchema::class.java).mapTo(LinkedHashSet()) { klass ->
            ChangeLogResourceFiles(klass.packageName, listOf(MIGRATION_FILE_LOCATION), klass.classLoader)
        }
        val dbChange = ClassloaderChangeLog(changeLogResourceFiles)

        dataSource.connection.use { connection ->
            schemaMigrator.updateDb(connection, dbChange, LiquibaseSchemaMigrator.PUBLIC_SCHEMA)
        }
    }

    /** Creates an `EntityManagerFactory` using the [dataSource]. */
    private fun createEntityManagerFactory(dataSource: DataSource) = entityManagerFactoryFactory.create(
        PERSISTENCE_UNIT_NAME,
        listOf(ConfigEntity::class.java, ConfigAuditEntity::class.java),
        DbEntityManagerConfiguration(dataSource)
    )

    /** Creates a [DataSource] using the [config]. */
    private fun createDataSource(config: Config): DataSource {
        val driver = getConfigStringOrDefault(config, CONFIG_DB_DRIVER, CONFIG_DB_DRIVER_DEFAULT)
        val jdbcUrl = getConfigStringOrDefault(config, CONFIG_JDBC_URL, CONFIG_JDBC_URL_DEFAULT)
        val username = getConfigStringOrDefault(config, CONFIG_DB_USER, CONFIG_DB_USER_DEFAULT)
        val password = getConfigStringOrDefault(config, CONFIG_DB_PASS, CONFIG_DB_PASS_DEFAULT)

        return HikariDataSourceFactory().create(driver, jdbcUrl, username, password, false, MAX_POOL_SIZE)
    }

    /** Returns the string at [path] from [config], or [default] if the path doesn't exist. */
    private fun getConfigStringOrDefault(config: Config, path: String, default: String) =
        if (config.hasPath(path)) config.getString(path) else default
}