package net.corda.processors.db.internal

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
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
import net.corda.processors.db.DBProcessorException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.sql.SQLException
import javax.sql.DataSource

/** The processor for a `DBWorker`. */
@Suppress("Unused")
@Component(service = [DBProcessor::class])
class DBProcessorImpl @Activate constructor(
    @Reference(service = ConfigWriteService::class)
    private val configWriteService: ConfigWriteService,
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
    }

    override fun stop() {
        configWriteService.stop()
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
        val migrationFileLocation = "net/corda/db/schema/config/db.changelog-master.xml"
        val changeLogResourceFiles = setOf(DbSchema::class.java).mapTo(LinkedHashSet()) { klass ->
            ChangeLogResourceFiles(klass.packageName, listOf(migrationFileLocation), klass.classLoader)
        }
        val dbChange = ClassloaderChangeLog(changeLogResourceFiles)

        try {
            dataSource.connection.use { connection ->
                schemaMigrator.updateDb(connection, dbChange, LiquibaseSchemaMigrator.PUBLIC_SCHEMA)
            }
        } catch (e: SQLException) {
            throw DBProcessorException("Could not connect to cluster database.", e)
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
        val fallbackConfig = ConfigFactory.empty()
            .withValue(CONFIG_DB_DRIVER, ConfigValueFactory.fromAnyRef(CONFIG_DB_DRIVER_DEFAULT))
            .withValue(CONFIG_JDBC_URL, ConfigValueFactory.fromAnyRef(CONFIG_JDBC_URL_DEFAULT))
            .withValue(CONFIG_MAX_POOL_SIZE, ConfigValueFactory.fromAnyRef(CONFIG_MAX_POOL_SIZE_DEFAULT))
        val configWithFallback = config.withFallback(fallbackConfig)

        val driver = configWithFallback.getString(CONFIG_DB_DRIVER)
        val jdbcUrl = configWithFallback.getString(CONFIG_JDBC_URL)
        val maxPoolSize = configWithFallback.getInt(CONFIG_MAX_POOL_SIZE)
        
        val username = getConfigStringOrNull(config, CONFIG_DB_USER) ?: throw DBProcessorException(
            "No username provided to connect to cluster database. Pass the `-d cluster.user` flag at worker startup."
        )
        val password = getConfigStringOrNull(config, CONFIG_DB_PASS) ?: throw DBProcessorException(
            "No password provided to connect to cluster database. Pass the `-d cluster.pass` flag at worker startup."
        )

        return HikariDataSourceFactory().create(driver, jdbcUrl, username, password, false, maxPoolSize)
    }

    /** Returns the string at [path] from [config], or null if the path doesn't exist. */
    private fun getConfigStringOrNull(config: Config, path: String) =
        if (config.hasPath(path)) config.getString(path) else null
}