package net.corda.db.persistence.testkit.components.impl

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DbPrivilege
import net.corda.db.persistence.testkit.components.DataSourceAdmin
import net.corda.db.schema.CordaDb
import net.corda.db.testkit.DbUtils
import net.corda.libs.configuration.SmartConfig
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesSet
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource

private data class NamedDataSource(val id: UUID, val name: String, val dataSource: CloseableDataSource)

@Suppress("unused", "TooManyFunctions")
@Component(service = [ DbConnectionManager::class, DataSourceAdmin::class ])
@ServiceRanking(Int.MAX_VALUE)
class DbConnectionManagerImpl @Activate constructor(
    @Reference
    private val emff: EntityManagerFactoryFactory,
    bundleContext: BundleContext
) : DbConnectionManager, DataSourceAdmin {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val dataSources = ConcurrentHashMap<UUID, NamedDataSource>()
    private var smartConfig: SmartConfig? = null
    private val schemaName: String

    init {
        schemaName = bundleContext.getProperty("testkit.schema.name") ?: "DUMMY-SCHEMA"
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
        logger.info("Started")
    }

    @Deactivate
    override fun stop() {
        dataSources.values.forEach { namedDataSource ->
            namedDataSource.dataSource.close()
        }
        logger.info("Stopped")
    }

    override fun getOrCreateDataSource(id: UUID, name: String): CloseableDataSource {
        return dataSources.computeIfAbsent(id) { dbId ->
            val configuration = DbUtils.getEntityManagerConfiguration(
                "testkit-db-manager-db-$schemaName",
                schemaName = "$schemaName$name".replace("-", ""),
                createSchema = true
            )
            NamedDataSource(dbId, name, configuration.dataSource)
        }.dataSource
    }

    override fun initialise(config: SmartConfig) {
        smartConfig = config
        logger.info("Initialised with {}", config)
    }

    override val clusterConfig: SmartConfig
        get() = smartConfig ?: throw IllegalStateException("Not initialized")

    override fun bootstrap(config: SmartConfig) {
    }

    override fun testAllConnections(): Boolean {
        return true
    }

    override fun putConnection(
        name: String,
        privilege: DbPrivilege,
        config: SmartConfig,
        description: String?,
        updateActor: String
    ): UUID {
        TODO("Not yet implemented")
    }

    override fun putConnection(
        entityManager: EntityManager,
        name: String,
        privilege: DbPrivilege,
        config: SmartConfig,
        description: String?,
        updateActor: String
    ): UUID {
        TODO("Not yet implemented")
    }

    override fun getClusterDataSource(): DataSource {
        TODO("Not yet implemented")
    }

    override fun createDatasource(connectionId: UUID): CloseableDataSource {
        return getOrCreateDataSource(connectionId, "")
    }

    override fun getDataSource(name: String, privilege: DbPrivilege): DataSource? {
        TODO("Not yet implemented")
    }

    override fun getDataSource(config: SmartConfig): CloseableDataSource {
        TODO("Not yet implemented")
    }

    override fun getClusterEntityManagerFactory(): EntityManagerFactory {
        TODO("Not yet implemented")
    }

    override fun getOrCreateEntityManagerFactory(db: CordaDb, privilege: DbPrivilege): EntityManagerFactory {
        TODO("Not yet implemented")
    }

    override fun getOrCreateEntityManagerFactory(
        name: String,
        privilege: DbPrivilege,
        entitiesSet: JpaEntitiesSet
    ): EntityManagerFactory {
        TODO("Not yet implemented")
    }

    override fun createEntityManagerFactory(connectionId: UUID, entitiesSet: JpaEntitiesSet): EntityManagerFactory {
        val source = dataSources[connectionId]
            ?: throw NoSuchElementException("No DataSource for connectionId=$connectionId")
        return emff.create(
            source.name,
            entitiesSet.classes.toList(),
            DbEntityManagerConfiguration(source.dataSource),
        )
    }

    override fun create(
        driverClass: String,
        jdbcUrl: String,
        username: String,
        password: String,
        isAutoCommit: Boolean,
        maximumPoolSize: Int
    ): CloseableDataSource {
        TODO("Not yet implemented")
    }
}