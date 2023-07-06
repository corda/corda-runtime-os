package net.corda.testing.driver.db

import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource
import net.corda.db.admin.DbChange
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DataSourceFactory
import net.corda.db.core.DbPrivilege
import net.corda.db.core.HikariDataSourceFactory
import net.corda.db.core.InMemoryDataSourceFactory
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
import net.corda.libs.configuration.SmartConfig
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.orm.TransactionIsolationLevel
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE_RANKING
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory

@Suppress("TooManyFunctions", "unused")
@Component(
    service = [ DbConnectionManager::class ],
    property = [ DRIVER_SERVICE ]
)
@ServiceRanking(DRIVER_SERVICE_RANKING)
class DbConnectionManagerImpl private constructor(
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
    private val liquibaseSchemaMigrator: LiquibaseSchemaMigrator,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val dataSourceFactory: DataSourceFactory
): DbConnectionManager, DataSourceFactory by dataSourceFactory {
    private companion object {
        private const val DRIVER_DB_NAME = "test-driver"
    }

    private val entityManagerFactories = ConcurrentHashMap<String, EntityManagerFactory>()
    private val logger = LoggerFactory.getLogger(this::class.java)
    private var smartConfig: SmartConfig? = null

    @Activate
    constructor(
        @Reference
        entityManagerFactoryFactory: EntityManagerFactoryFactory,
        @Reference
        jpaEntitiesRegistry: JpaEntitiesRegistry,
        @Reference
        liquibaseSchemaMigrator: LiquibaseSchemaMigrator
    ) : this(
        entityManagerFactoryFactory = entityManagerFactoryFactory,
        liquibaseSchemaMigrator = liquibaseSchemaMigrator,
        jpaEntitiesRegistry = jpaEntitiesRegistry,
        dataSourceFactory = HikariDataSourceFactory()
    )

    @Deactivate
    fun done() {
        entityManagerFactories.clear()
        dataSource.destroy()
    }

    private fun loadVaultSchema(): DbChange {
        return object : DbChange {
            override val masterChangeLogFiles: List<String>
                get() = listOf(
                    "net/corda/db/schema/vnode-uniqueness/db.changelog-master.xml",
                    "net/corda/db/schema/vnode-vault/db.changelog-master.xml"
                )
            override val changeLogFileList: Set<String>
                get() = emptySet()
            override fun fetch(path: String): InputStream {
                return DbSchema::class.java.getResourceAsStream(path.removePrefix("net/corda/db/schema/"))
                    ?: throw FileNotFoundException("Cannot locate $path")
            }
        }
    }

    private val dataSource = DriverDataSource(InMemoryDataSourceFactory(dataSourceFactory).create(DRIVER_DB_NAME).also { db ->
        db.connection.use { connection ->
            liquibaseSchemaMigrator.updateDb(connection, loadVaultSchema())
        }
    })

    override val isRunning: Boolean
        get() = true

    override fun start() {
        logger.info("Started")
    }

    override fun stop() {
        logger.info("Stopped")
    }

    override fun initialise(config: SmartConfig) {
        smartConfig = config
        logger.info("Initialised with {}", config)
    }

    override val clusterConfig: SmartConfig
        get() = smartConfig ?: throw IllegalStateException("Not initialised")

    override fun bootstrap(config: SmartConfig) {
    }

    override fun testConnection() = true

    override fun putConnection(
        name: String,
        privilege: DbPrivilege,
        config: SmartConfig,
        description: String?,
        updateActor: String
    ): UUID {
        throw UnsupportedOperationException("putConnection - not supported")
    }

    override fun putConnection(
        entityManager: EntityManager,
        name: String,
        privilege: DbPrivilege,
        config: SmartConfig,
        description: String?,
        updateActor: String
    ): UUID {
        throw UnsupportedOperationException("putConnection - not supported")
    }

    override fun getClusterDataSource(): DataSource {
        return dataSource
    }

    override fun createDatasource(connectionId: UUID): CloseableDataSource {
        return dataSource
    }

    override fun getDataSource(name: String, privilege: DbPrivilege): DataSource {
        return dataSource
    }

    override fun getDataSource(config: SmartConfig): CloseableDataSource {
        return dataSource
    }

    override fun getClusterEntityManagerFactory(): EntityManagerFactory {
        throw UnsupportedOperationException("getClusterEntityManagerFactory - not supported")
    }

    override fun getOrCreateEntityManagerFactory(db: CordaDb, privilege: DbPrivilege): EntityManagerFactory {
        return getOrCreateEntityManagerFactory(
            db.persistenceUnitName,
            privilege,
            jpaEntitiesRegistry.get(db.persistenceUnitName)
                ?: throw IllegalArgumentException("Unknown database $db")
        )
    }

    override fun getOrCreateEntityManagerFactory(
        name: String,
        privilege: DbPrivilege,
        entitiesSet: JpaEntitiesSet
    ): EntityManagerFactory {
        return entityManagerFactories.computeIfAbsent(name) { persistenceUnitName ->
            createEntityManagerFactory(persistenceUnitName, entitiesSet)
        }
    }

    override fun createEntityManagerFactory(connectionId: UUID, entitiesSet: JpaEntitiesSet): EntityManagerFactory {
        logger.info("Loading DB connection details for {}", connectionId)
        return createEntityManagerFactory(connectionId.toString(), entitiesSet)
    }

    private fun createEntityManagerFactory(name: String, entitiesSet: JpaEntitiesSet): EntityManagerFactory {
        return entityManagerFactoryFactory.create(
            name,
            entitiesSet.classes.toList(),
            DriverEntityManagerConfiguration(
                dataSource = dataSource,
                showSql = false,
                formatSql = true,
                jdbcTimezone = "UTC",
                ddlManage = DdlManage.UPDATE,
                transactionIsolationLevel = TransactionIsolationLevel.SERIALIZABLE
            )
        )
    }
}

private class DriverDataSource(private val dataSource: CloseableDataSource) : CloseableDataSource, DataSource by dataSource  {
    override fun close() {}

    @Throws(IOException::class)
    fun destroy() {
        dataSource.close()
    }
}
