package net.corda.processors.db.internal.db

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
import java.sql.Connection
import javax.persistence.EntityManager
import javax.sql.DataSource

// TODO - Joel - Make this into a helper class. Should be stateless.

/** An implementation of [DBWriter]. */
@Suppress("Unused")
@Component(service = [DBWriter::class])
class DBWriterImpl @Activate constructor(
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
    @Reference(service = LiquibaseSchemaMigrator::class)
    private val schemaMigrator: LiquibaseSchemaMigrator
) : DBWriter {

    private var dataSourceConnection: Connection? = null
    private var entityManager: EntityManager? = null

    override fun start() = Unit

    override fun bootstrapConfig(config: SmartConfig, managedEntities: Iterable<Class<*>>) {
        if (dataSourceConnection != null || entityManager != null) {
            throw DBWriteException("An attempt was made to set the bootstrap configuration twice.")
        }

        // TODO - Joel - Do I need to check whether the DB is ready yet?

        // TODO - Joel - Create these on the fly.
        val dataSource = createDataSource(config)
        migrateDb(managedEntities, dataSource)
        // TODO - Joel - Create these on the fly.
        entityManager = createEntityManager(managedEntities, dataSource)
        this.dataSourceConnection = dataSource.connection
    }

    override fun stop() {
        dataSourceConnection?.close()
        entityManager?.close()
    }

    override fun writeEntity(entities: Iterable<Any>) {
        val entityManager = entityManager ?: throw DBWriteException(
            "An attempt was made to write an entity before bootstrapping the config."
        )

        entityManager.transaction.begin()
        entities.forEach { entity ->
            entityManager.merge(entity)
        }
        entityManager.transaction.commit()
    }

    override val isRunning get() = dataSourceConnection != null && entityManager != null

    /** Creates a [DataSource] for the cluster database. */
    private fun createDataSource(config: SmartConfig): DataSource {
        // TODO - Joel - Define fallback for driver, username and password.
        val driver = config.getString(CONFIG_DB_DRIVER)
        val jdbcUrl = config.getString(CONFIG_JDBC_URL)
        val username = config.getString(CONFIG_DB_USER)
        val password = config.getString(CONFIG_DB_PASS)

        // TODO - Joel - Pass this in.
        return HikariDataSourceFactory().create(driver, jdbcUrl, username, password, false, MAX_POOL_SIZE)
    }

    // TODO - Joel - Is it OK to use a single entity manager for the lifetime of this component? Check with Dries.
    /** Creates an entity manager for the given [managedEntities] and [dataSource]. */
    private fun createEntityManager(managedEntities: Iterable<Class<*>>, dataSource: DataSource) =
        entityManagerFactoryFactory.create(
            PERSISTENCE_UNIT_NAME, managedEntities.toList(), DbEntityManagerConfiguration(dataSource)
        ).createEntityManager()

    /**
     * Applies the Liquibase schema migrations for the [managedEntities].
     *
     * TODO - Joel - Will be handled by a different component in the future.
     */
    private fun migrateDb(managedEntities: Iterable<Class<*>>, dataSource: DataSource) {
        val changeLogResourceFiles = managedEntities.mapTo(LinkedHashSet()) { entity ->
            ChangeLogResourceFiles(entity.packageName, listOf(MIGRATION_FILE_LOCATION), entity.classLoader)
        }
        val dbChange = ClassloaderChangeLog(changeLogResourceFiles)
        schemaMigrator.updateDb(dataSource.connection, dbChange, LiquibaseSchemaMigrator.PUBLIC_SCHEMA)
    }
}