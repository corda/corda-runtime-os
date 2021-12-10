package net.corda.processors.db.internal.config.writer

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

/** Encapsulates database-related functionality, so that it can be substituted during tests. */
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
     * This is a temporary measure. Migrations will be applied by a different codepath in the future.
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

        entityManager.transaction.begin()
        entities.forEach { entity ->
            entityManager.merge(entity)
        }
        entityManager.transaction.commit()
    }

    /** Creates a [DataSource] for the cluster database. */
    private fun createDataSource(): DataSource {
        // TODO - Joel - Define fallback for driver, username and password.
        val driver = config.getString(CONFIG_DB_DRIVER)
        val jdbcUrl = config.getString(CONFIG_JDBC_URL)
        val username = config.getString(CONFIG_DB_USER)
        val password = config.getString(CONFIG_DB_PASS)

        return dataSourceFactory.create(driver, jdbcUrl, username, password, false, MAX_POOL_SIZE)
    }

    /** Creates an entity manager. */
    private fun createEntityManager(): EntityManager {
        val dataSource = createDataSource()
        return entityManagerFactoryFactory.create(
            PERSISTENCE_UNIT_NAME, managedEntities.toList(), DbEntityManagerConfiguration(dataSource)
        ).createEntityManager()
    }
}