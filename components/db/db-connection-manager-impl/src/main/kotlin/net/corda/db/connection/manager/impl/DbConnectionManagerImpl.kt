package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.db.connection.manager.createFromConfig
import net.corda.db.connection.manager.impl.lifecyclewrappers.DownOnErrorCloseableDataSource
import net.corda.db.connection.manager.impl.lifecyclewrappers.DownOnErrorDataSource
import net.corda.db.connection.manager.impl.lifecyclewrappers.DownOnErrorEntityManagerFactory
import net.corda.db.connection.manager.impl.lifecyclewrappers.downOnError
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DataSourceFactory
import net.corda.db.core.DbPrivilege
import net.corda.db.core.HikariDataSourceFactory
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource

@Component(service = [DbConnectionManager::class])
@Suppress("LongParameterList", "TooManyFunctions")
class DbConnectionManagerImpl (
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val dataSourceFactory: DataSourceFactory,
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
    private val entitiesRegistry: JpaEntitiesRegistry,
    private val dbConnectionRepositoryFactory: DbConnectionRepositoryFactory,
    private val dbConnectionOps: DbConnectionOps,
    private val checkConnectionRetryTimeout: Duration,
    private val sleeper: (d: Duration) -> Unit
): DbConnectionManager, DbConnectionOps, DataSourceFactory {

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = EntityManagerFactoryFactory::class)
        entityManagerFactoryFactory: EntityManagerFactoryFactory,
        @Reference(service = JpaEntitiesRegistry::class)
        entitiesRegistry: JpaEntitiesRegistry,
    ) :
            this(
                lifecycleCoordinatorFactory,
                HikariDataSourceFactory(),
                entityManagerFactoryFactory,
                entitiesRegistry,
                DbConnectionRepositoryFactory(),
                LateInitDbConnectionOps(),
                Duration.ofSeconds(3),
                { d -> Thread.sleep(d.toMillis()) })

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val eventHandler = DbConnectionManagerEventHandler(this)
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator<DbConnectionManager>(eventHandler)
    private lateinit var lateInitialisedConfig: SmartConfig
    private var dbConnectionsRepository: DbConnectionsRepository? = null

    override val clusterConfig: SmartConfig
        get() {
            if(!this::lateInitialisedConfig.isInitialized)
                throw DBConfigurationException("Cluster DB must be initialised.")
            return lateInitialisedConfig
        }

    override fun bootstrap(config: SmartConfig) {
        lifecycleCoordinator.postEvent(BootstrapConfigProvided(config))
    }

    override fun testConnection(): Boolean = try {
        checkDatabaseConnection(getClusterDataSource())
        true
    }  catch (e: DBConfigurationException) {
        logger.debug("DB check failed", e)
        false
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }

    @Deactivate
    fun close() {
        lifecycleCoordinator.close()
    }

    /**
     * Initialise the [DbConnectionManagerImpl] with the given Cluster DB config.
     *
     * This also validates we can connect to the configured cluster DB and retries until it is successful.
     */
    override fun initialise(config: SmartConfig) {
        // configure connection to cluster DB and try/retry to connect
        this.lateInitialisedConfig = config
        val clusterDataSource = dataSourceFactory.createFromConfig(config)
        val clusterEntityManagerFactory = createManagerFactory(CordaDb.CordaCluster.persistenceUnitName, clusterDataSource)
        val dbConnectionsRepository = dbConnectionRepositoryFactory.create(
            clusterDataSource, dataSourceFactory, clusterEntityManagerFactory, config.factory)
        this.dbConnectionsRepository = dbConnectionsRepository
        if (dbConnectionOps is LateInitDbConnectionOps) {
            dbConnectionOps.delegate = DbConnectionOpsCachedImpl(
                DbConnectionOpsImpl(dbConnectionsRepository, entitiesRegistry, entityManagerFactoryFactory),
                entitiesRegistry)
        }

        /**
         * This will retry to connect infinitely until it has a successful connection.
         * This is because the components that rely on this (i.e. the entire DB Processor) cannot do anything
         * until it has a successful connection to the cluster DB.
         * We have to rely on manual intervention to resolve it.
         * Otherwise, the danger with giving up is that we can't recover unless restarting the process completely anyway.
         * In a K8s setting we have to be careful that doesn't just trigger pods being terminated and re-created
         * because K8s thinks they're unhealthy while it's actually the downstream system.
         */
        while (true) {
            try {
                checkDatabaseConnection(clusterDataSource)
                logger.info("Connection to Cluster DB is successful.")
                eventHandler.scheduleNextDbCheck(lifecycleCoordinator)
                return
            } catch (e: DBConfigurationException) {
                logger.warn("Failed to connect to Cluster DB. " +
                        "Will be retrying in ${checkConnectionRetryTimeout.seconds}s: $e")
                sleeper(checkConnectionRetryTimeout)
            }
        }
    }

    /**
     * Creates [EntityManagerFactory] from given [DataSource] and name used to lookpup
     * registered entity classes.
     *
     * @param name Name for lookup in [JpaEntitiesRegistry] to get entity classes
     * @param dataSource DataSource
     */
    private fun createManagerFactory(name: String, dataSource: CloseableDataSource): EntityManagerFactory {
        logger.debug { "Creating EntityManagerFactory for persistence unit $name" }
        return entityManagerFactoryFactory.create(
            name,
            entitiesRegistry.get(name)?.classes?.toList() ?:
            throw DBConfigurationException("Entity set for $name not found"),
            DbEntityManagerConfiguration(dataSource),
        )
    }

    /**
     * Checks that it is possible to connect to the cluster database using the [dataSource].
     *
     * @throws DBConfigurationException If the cluster database cannot be connected to.
     */
    private fun checkDatabaseConnection(dataSource: DataSource) = try {
        dataSource.connection.close()
    } catch (e: Exception) {
        throw DBConfigurationException("Could not connect to cluster database.", e)
    }

    override fun create(
        driverClass: String,
        jdbcUrl: String,
        username: String,
        password: String,
        isAutoCommit: Boolean,
        maximumPoolSize: Int
    ): CloseableDataSource {
        val closeableDataSource = lifecycleCoordinator.downOnError {
            dataSourceFactory.create(driverClass, jdbcUrl, username, password, isAutoCommit, maximumPoolSize)
        }

        return DownOnErrorCloseableDataSource(lifecycleCoordinator, closeableDataSource)
    }

    override fun putConnection(
        name: String,
        privilege: DbPrivilege,
        config: SmartConfig,
        description: String?,
        updateActor: String
    ): UUID =
        lifecycleCoordinator.downOnError { dbConnectionOps.putConnection(name, privilege, config, description, updateActor) }

    override fun putConnection(
        entityManager: EntityManager,
        name: String,
        privilege: DbPrivilege,
        config: SmartConfig,
        description: String?,
        updateActor: String
    ): UUID =
        lifecycleCoordinator.downOnError { dbConnectionOps.putConnection(entityManager, name, privilege, config, description, updateActor) }

    override fun getClusterDataSource(): DataSource {
        val clusterDataSource = lifecycleCoordinator.downOnError {
            dbConnectionOps.getClusterDataSource()
        }
        return DownOnErrorDataSource(lifecycleCoordinator, clusterDataSource)
    }

    override fun createDatasource(connectionId: UUID): CloseableDataSource {
        val dataSource = lifecycleCoordinator.downOnError { dbConnectionOps.createDatasource(connectionId) }
        return DownOnErrorCloseableDataSource(lifecycleCoordinator, dataSource)
    }

    override fun getDataSource(name: String, privilege: DbPrivilege): DataSource? =
        lifecycleCoordinator.downOnError { dbConnectionOps.getDataSource(name, privilege) }?.let { dataSource ->
            DownOnErrorDataSource(lifecycleCoordinator, dataSource)
        }

    override fun getDataSource(config: SmartConfig): CloseableDataSource {
        val closeableDataSource = lifecycleCoordinator.downOnError { dbConnectionOps.getDataSource(config) }
        return DownOnErrorCloseableDataSource(lifecycleCoordinator, closeableDataSource)
    }

    override fun getClusterEntityManagerFactory(): EntityManagerFactory =
        lifecycleCoordinator.downOnError { DownOnErrorEntityManagerFactory(
            lifecycleCoordinator,
            dbConnectionOps.getClusterEntityManagerFactory()
        ) }

    override fun getOrCreateEntityManagerFactory(db: CordaDb, privilege: DbPrivilege): EntityManagerFactory =
        lifecycleCoordinator.downOnError { DownOnErrorEntityManagerFactory(
            lifecycleCoordinator,
            dbConnectionOps.getOrCreateEntityManagerFactory(db, privilege)
        ) }

    override fun getOrCreateEntityManagerFactory(
        name: String,
        privilege: DbPrivilege,
        entitiesSet: JpaEntitiesSet
    ): EntityManagerFactory = lifecycleCoordinator.downOnError { DownOnErrorEntityManagerFactory(
        lifecycleCoordinator,
        dbConnectionOps.getOrCreateEntityManagerFactory(name, privilege, entitiesSet)
    ) }

    override fun createEntityManagerFactory(connectionId: UUID, entitiesSet: JpaEntitiesSet): EntityManagerFactory =
        lifecycleCoordinator.downOnError { DownOnErrorEntityManagerFactory(
            lifecycleCoordinator,
            dbConnectionOps.createEntityManagerFactory(connectionId, entitiesSet)
        ) }
}
