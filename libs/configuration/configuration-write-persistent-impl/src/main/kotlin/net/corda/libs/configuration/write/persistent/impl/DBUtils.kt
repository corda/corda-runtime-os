package net.corda.libs.configuration.write.persistent.impl

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
    private val dataSourceFactory = HikariDataSourceFactory()
    private val managedEntities = setOf(ConfigEntity::class.java)

    /**
     * Connects to the cluster database using the [config], and applies the Liquibase schema migrations for each of the
     * [managedEntities].
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
     * Connects to the cluster database using the [config] and writes all the [entities] to the cluster database.
     *
     * Each of the [entities] must be an instance of a class annotated with `@Entity`.
     *
     * @throws RollbackException If the database transaction cannot be committed.
     * @throws IllegalStateException/IllegalArgumentException/TransactionRequiredException If writing the entities
     *  fails for any other reason.
     */
    fun writeEntity(config: SmartConfig, entities: Iterable<Any>) {
        val entityManager = createEntityManager(config)

        try {
            entityManager.transaction.begin()
            entities.forEach(entityManager::merge)
            entityManager.transaction.commit()

        } finally {
            entityManager.close()
        }
    }

    /** Connects to the cluster database using the [config] and creates an [EntityManager]. */
    private fun createEntityManager(config: SmartConfig): EntityManager {
        val dataSource = createDataSource(config)
        return entityManagerFactoryFactory.create(
            PERSISTENCE_UNIT_NAME, managedEntities.toList(), DbEntityManagerConfiguration(dataSource)
        ).createEntityManager()
    }

    /** Creates a [DataSource] for the cluster database using the [config]. */
    private fun createDataSource(config: SmartConfig): DataSource {
        val driver = config.getString(CONFIG_DB_DRIVER)
        val jdbcUrl = config.getString(CONFIG_JDBC_URL)
        val username = config.getString(CONFIG_DB_USER)
        val password = config.getString(CONFIG_DB_PASS)

        return dataSourceFactory.create(driver, jdbcUrl, username, password, false, MAX_POOL_SIZE)
    }
}