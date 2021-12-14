package net.corda.libs.configuration.write.persistent.impl

import com.typesafe.config.ConfigException
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.core.HikariDataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.sql.SQLException
import javax.persistence.EntityManager
import javax.persistence.RollbackException
import javax.sql.DataSource

/** Encapsulates database-related functionality, so that it can be stubbed during tests. */
@Component(service = [DBUtils::class])
class DBUtils @Activate constructor(
    @Reference(service = LiquibaseSchemaMigrator::class)
    private val schemaMigrator: LiquibaseSchemaMigrator,
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory
) {
    companion object {
        private val managedEntities = setOf(ConfigEntity::class.java)
        private val dataSourceFactory = HikariDataSourceFactory()
    }

    /**
     * Checks the connection to the cluster database.
     *
     * @throws ConfigWriterException If the cluster database cannot be connected to.
     */
    fun checkClusterDatabaseConnection(config: SmartConfig) {
        val dataSource = createDataSource(config)
        try {
            dataSource.connection.close()
        } catch (e: SQLException) {
            throw ConfigWriterException("Could not connect to cluster database.", e)
        }
    }

    /**
     * Connects to the cluster database, and applies the Liquibase schema migrations for the [managedEntities].
     *
     * This is a temporary measure. Migrations will be applied by a different code-path in the future.
     */
    fun migrateClusterDatabase(config: SmartConfig) {
        val changeLogResourceFiles = managedEntities.mapTo(LinkedHashSet()) { entity ->
            ChangeLogResourceFiles(entity.packageName, listOf(MIGRATION_FILE_LOCATION), entity.classLoader)
        }
        val dbChange = ClassloaderChangeLog(changeLogResourceFiles)

        createDataSource(config).connection.use { connection ->
            schemaMigrator.updateDb(connection, dbChange, LiquibaseSchemaMigrator.PUBLIC_SCHEMA)
        }
    }

    /**
     * Writes all the [entities] to the cluster database.
     *
     * Each of the [entities] must be an instance of a class annotated with `@Entity`.
     *
     * @throws RollbackException If the database transaction cannot be committed.
     * @throws IllegalStateException/IllegalArgumentException/TransactionRequiredException If writing the entities
     *  fails for any other reason.
     */
    fun writeEntity(
        entities: Iterable<Any>,
        config: SmartConfig
    ) {
        val entityManager = createEntityManager(config)

        try {
            entityManager.transaction.begin()
            entities.forEach { entity ->
                entityManager.merge(entity)
            }
            entityManager.transaction.commit()
        } finally {
            entityManager.close()
        }
    }

    /** Creates an entity manager. */
    private fun createEntityManager(
        config: SmartConfig
    ): EntityManager {
        val dataSource = createDataSource(config)
        return entityManagerFactoryFactory.create(
            PERSISTENCE_UNIT_NAME, managedEntities.toList(), DbEntityManagerConfiguration(dataSource)
        ).createEntityManager()
    }

    /** Creates a [DataSource] for the cluster database. */
    private fun createDataSource(config: SmartConfig): DataSource {
        val driver = getConfigStringOrDefault(config, CONFIG_DB_DRIVER, CONFIG_DB_DRIVER_DEFAULT)
        val jdbcUrl = getConfigStringOrDefault(config, CONFIG_JDBC_URL, CONFIG_JDBC_URL_DEFAULT)
        val username = getConfigStringOrDefault(config, CONFIG_DB_USER, CONFIG_DB_USER_DEFAULT)
        val password = getConfigStringOrDefault(config, CONFIG_DB_PASS, CONFIG_DB_PASS_DEFAULT)

        return dataSourceFactory.create(driver, jdbcUrl, username, password, false, MAX_POOL_SIZE)
    }

    /** Returns [path] from [config], or default if [path] does not exist. */
    private fun getConfigStringOrDefault(config: SmartConfig, path: String, default: String) = try {
        config.getString(path)
    } catch (e: ConfigException.Missing) {
        default
    }
}