package net.corda.processors.db.internal.config.writer

import com.typesafe.config.ConfigException
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.core.DataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import java.sql.SQLException
import javax.persistence.EntityManager
import javax.persistence.RollbackException
import javax.sql.DataSource

/** Encapsulates database-related functionality, so that it can be stubbed during tests. */
class DBUtils(
    private val config: SmartConfig,
    private val schemaMigrator: LiquibaseSchemaMigrator,
    private val dataSourceFactory: DataSourceFactory,
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
    private val managedEntities: Iterable<Class<*>>
) {
    /**
     * Checks the connection to the cluster database.
     *
     * @throws ConfigWriteException If the cluster database cannot be connected to.
     */
    fun checkClusterDatabaseConnection() {
        val dataSource = createDataSource()
        try {
            dataSource.connection.close()
        } catch (e: SQLException) {
            throw ConfigWriteException("Could not connect to cluster database.", e)
        }
    }

    /**
     * Connects to the cluster database, and applies the Liquibase schema migrations for the [managedEntities].
     *
     * This is a temporary measure. Migrations will be applied by a different code-path in the future.
     */
    fun migrateClusterDatabase() {
        val changeLogResourceFiles = managedEntities.mapTo(LinkedHashSet()) { entity ->
            ChangeLogResourceFiles(entity.packageName, listOf(MIGRATION_FILE_LOCATION), entity.classLoader)
        }
        val dbChange = ClassloaderChangeLog(changeLogResourceFiles)

        createDataSource().connection.use { connection ->
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
    fun writeEntity(entities: Iterable<Any>) {
        val entityManager = createEntityManager()

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

    /** Creates a [DataSource] for the cluster database. */
    private fun createDataSource(): DataSource {
        val driver = getConfigStringOrDefault(CONFIG_DB_DRIVER, CONFIG_DB_DRIVER_DEFAULT)
        val jdbcUrl = getConfigStringOrDefault(CONFIG_JDBC_URL, CONFIG_JDBC_URL_DEFAULT)
        val username = getConfigStringOrDefault(CONFIG_DB_USER, CONFIG_DB_USER_DEFAULT)
        val password = getConfigStringOrDefault(CONFIG_DB_PASS, CONFIG_DB_PASS_DEFAULT)

        return dataSourceFactory.create(driver, jdbcUrl, username, password, false, MAX_POOL_SIZE)
    }

    /** Creates an entity manager. */
    private fun createEntityManager(): EntityManager {
        val dataSource = createDataSource()
        return entityManagerFactoryFactory.create(
            PERSISTENCE_UNIT_NAME, managedEntities.toList(), DbEntityManagerConfiguration(dataSource)
        ).createEntityManager()
    }

    /** Returns [path] from [config], or default if [path] does not exist. */
    private fun getConfigStringOrDefault(path: String, default: String) = try {
        config.getString(path)
    } catch (e: ConfigException.Missing) {
        default
    }
}