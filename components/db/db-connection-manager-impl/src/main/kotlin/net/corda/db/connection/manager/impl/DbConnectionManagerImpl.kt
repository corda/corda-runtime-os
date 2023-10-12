package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.connection.manager.DbConnectionsRepository
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
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource

var instance: DbConnectionManagerImpl? = null

/**
 * Recommended entry point for creating an Entity Manager.
 *
 * Use this to get an EntityManager, which you should use and then quickly close. Caching and health checks
 * are handled internally.
 *
 * If the DB connection manager is not ready, or the database is down, or the network is unreliable, this will
 * sleep until the database is available. In that time, the health check will be negative, so the operator may
 * choose to restart the whole container (and that happens automatically on Kubernetes using our Helm charts).
 *
 * @param privilege The privilege level of the entity manager - DML or DDL.
 * @param name Optionally, the database name to connect to
 * @param name Optionally, connection ID in the DB Connection Manager records to use.
 * @param persistenceUnitName Optionally, the persistence units to expose
 * @return An EntityManager, that is closeable
 *
 * It is suggested that classes using this have a constructor parameter which defaults to this, so that it can be
 * overriden for local use. To keep that concise, we deliberately use a few defaulted arguments.
 */
fun makeEntityManager(
    privilege: DbPrivilege=DbPrivilege.DML,
    name: CordaDb?=null,
    connectionId: UUID?=null,
    persistenceUnitName: String? = null): EntityManager {
    while (instance == null) {
        DbConnectionManagerImpl.logger.info("Wating for DBConnectionManager to appear connecting to $name at privilege $privilege")
        Thread.sleep(1000)
    }
    while (!instance!!.isRunning) {
        DbConnectionManagerImpl.logger.info("Wating for DBConnectionManager to become ready connecting to $name at privilege $privilege")
        Thread.sleep(1000)
    }
    val emf: EntityManagerFactory = if (name == CordaDb.CordaCluster) {
        require(connectionId == null)
        instance!!.getOrCreateEntityManagerFactory(name, privilege)
    } else {
        require(name != null)
        if (connectionId != null) {
            require(persistenceUnitName != null)
            instance!!.createEntityManagerFactory(
                connectionId = connectionId,
                entitiesSet = instance!!.entitiesRegistry.get(persistenceUnitName)?: throw IllegalStateException(
                    "persistenceUnitName ${persistenceUnitName} is not registered."
                )
            )
        } else {
            instance!!.getClusterEntityManagerFactory() // TODO change
        }
    }
    return emf.createEntityManager()
}

@Component(service = [DbConnectionManager::class])
@Suppress("LongParameterList")
class DbConnectionManagerImpl (
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val dataSourceFactory: DataSourceFactory,
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
    internal val entitiesRegistry: JpaEntitiesRegistry,
    private val dbConnectionRepositoryFactory: DbConnectionRepositoryFactory,
    private val dbConnectionOps: DbConnectionOps,
    private val checkConnectionRetryTimeout: Duration,
    private val sleeper: (d: Duration) -> Unit
): DbConnectionManager, DbConnectionOps by dbConnectionOps, DataSourceFactory by dataSourceFactory {

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
                { d -> Thread.sleep(d.toMillis()) })  {
                logger.info("Constructed DbConnectionManager $this")
                instance = this
            }

    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
     * Creates [EntityManagerFactory] from given [DataSource] and name used to lookup
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
}
