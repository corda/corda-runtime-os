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

// TODO - Joel - Ideally, this class would use a coordinator for starting/setup/stopping. However, need to check I'm
//  not duplicating other work before I develop this component too far.

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

    override fun writeEntity(entities: Iterable<Any>) {
        val entityManager =
            entityManager ?: throw IllegalArgumentException("TODO - Joel - Change exception type and create message.")

        entityManager.transaction.begin()
        entities.forEach { entity ->
            entityManager.merge(entity)
        }
        entityManager.transaction.commit()
    }

    override fun start() = Unit

    override fun bootstrapConfig(config: SmartConfig, managedEntities: Iterable<Class<*>>) {
        if (dataSourceConnection != null || entityManager != null) {
            throw IllegalArgumentException("TODO - Joel - Change exception type and create message.")
        }

        val dataSource = createDataSource(config)
        migrateDb(managedEntities, dataSource)
        entityManager = createEntityManager(managedEntities, dataSource)
        this.dataSourceConnection = dataSource.connection
    }

    override fun stop() {
        dataSourceConnection?.close()
        entityManager?.close()
    }

    override val isRunning get() = dataSourceConnection != null && entityManager != null

    /** Creates a [DataSource] for the cluster database. */
    private fun createDataSource(config: SmartConfig): DataSource {
        val driver = config.getString(CONFIG_DB_DRIVER)
        val jdbcUrl = config.getString(CONFIG_JDBC_URL)
        val username = config.getString(CONFIG_DB_USER)
        val password = config.getString(CONFIG_DB_PASS)

        return HikariDataSourceFactory().create(driver, jdbcUrl, username, password, false, MAX_POOL_SIZE)
    }

    // TODO - Joel - Understand this better. Can I just use a single entity manager for the lifetime of this component?
    /** Creates an entity manager for the given [managedEntities] and [dataSource]. */
    private fun createEntityManager(managedEntities: Iterable<Class<*>>, dataSource: DataSource) =
        entityManagerFactoryFactory.create(
            PERSISTENCE_UNIT_NAME, managedEntities.toList(), DbEntityManagerConfiguration(dataSource)
        ).createEntityManager()

    // TODO - Joel - Move this migration to its proper place.
    /** Applies the Liquibase schema migrations for the [managedEntities]. */
    private fun migrateDb(managedEntities: Iterable<Class<*>>, dataSource: DataSource) {
        // TODO - Joel - This is using `impl` classes. Check with Dries this is correct.
        val changeLogResourceFiles = managedEntities.mapTo(LinkedHashSet()) { entity ->
            ChangeLogResourceFiles(entity.packageName, listOf(MIGRATION_FILE_LOCATION), entity.classLoader)
        }
        val dbChange = ClassloaderChangeLog(changeLogResourceFiles)
        schemaMigrator.updateDb(dataSource.connection, dbChange, LiquibaseSchemaMigrator.PUBLIC_SCHEMA)
    }
}