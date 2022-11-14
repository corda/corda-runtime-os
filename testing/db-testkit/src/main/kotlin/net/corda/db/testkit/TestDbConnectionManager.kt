package net.corda.db.testkit

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesSet
import net.corda.v5.base.util.loggerFor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource
import kotlin.NoSuchElementException

@Suppress("unused", "TooManyFunctions")
@Component(service = [DbConnectionManager::class, TestDbConnectionManagerAdmin::class])
@ServiceRanking(Int.MAX_VALUE)
class TestDbConnectionManager @Activate constructor(
    @Reference
    private val emff: EntityManagerFactoryFactory,
) : DbConnectionManager, TestDbConnectionManagerAdmin {
    private val logger = loggerFor<TestDbConnectionManager>()

    private val createdDataSources = mutableListOf<CloseableDataSource>()
    private val connectionConfigurations = ConcurrentHashMap<UUID, NamedDataSourceConfiguration>()
    private var smartConfig: SmartConfig? = null
    private val schemaName: String = "DUMMY-SCHEMA"

    override val isRunning: Boolean
        get() = true

    override fun start() {
        logger.info("Started")
    }

    @Deactivate
    override fun stop() {
        createdDataSources.map { it.connection }.filterNot { it.isClosed }.forEach { it.close() }
        logger.info("Stopped")
    }

    override fun getOrCreateDataSource(id: UUID, name: String): CloseableDataSource {
        val connectionConfiguration = connectionConfigurations.computeIfAbsent(id) { dbId ->
            NamedDataSourceConfiguration(
                id = dbId,
                name = name,
                inMemoryDbName = "testkit-db-manager-db-$schemaName$name",
                dbUser = "postgres", // Only used for local postgress (if selected)
                dbPassword = "password", // Only used for local postgress (if selected)
                schemaName = "$schemaName$name".replace("-", ""),
                createSchema = true
            )
        }

        return createDataSource(connectionConfiguration)
    }

    override fun initialise(config: SmartConfig) {
        smartConfig = config
        logger.info("Initialised with {}", config)
    }

    override val clusterConfig: SmartConfig
        get() = smartConfig ?: throw IllegalStateException("Not initialized")

    override fun bootstrap(config: SmartConfig) {
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
        val connectionConfig = checkNotNull(connectionConfigurations[connectionId]) {
            "connection ID '$connectionId' not found"
        }

        return emff.create(
            connectionConfig.name,
            entitiesSet.classes.toList(),
            DbEntityManagerConfiguration(createDataSource(connectionConfig)),
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

    private fun createDataSource(configuration: NamedDataSourceConfiguration): CloseableDataSource {
        val newConnection = DbUtils.getEntityManagerConfiguration(
            configuration.inMemoryDbName,
            configuration.dbUser,
            configuration.dbPassword,
            configuration.schemaName,
            configuration.createSchema
        )

        return newConnection.dataSource
    }

    private data class NamedDataSourceConfiguration(
        val id: UUID,
        val name: String,
        val inMemoryDbName: String,
        val dbUser: String? = null,
        val dbPassword: String? = null,
        val schemaName: String? = null,
        val createSchema: Boolean = false
    )
}