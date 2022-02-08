package net.corda.db.connection.manager.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.db.connection.manager.createFromConfig
import net.corda.db.core.DataSourceFactory
import net.corda.db.core.DbPrivilege
import net.corda.db.core.HikariDataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.configuration.datamodel.DbConnectionConfig
import net.corda.libs.configuration.datamodel.findDbConnectionByNameAndPrivilege
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource

/**
 * Repository for DB connections fetched from the Connections DB.
 *
 * Throws exception when trying to fetch a connection before the Cluster connection has been initialised.
 */
@Component(service = [DbConnectionsRepository::class])
class DbConnectionsRepositoryImpl(
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
    private val dataSourceFactory: DataSourceFactory,
    private val checkConnectionRetryTimeout: Duration,
    private val sleeper: (d: Duration) -> Unit,
): DbConnectionsRepository {

    @Activate
    constructor(
        @Reference(service = EntityManagerFactoryFactory::class)
        entityManagerFactoryFactory: EntityManagerFactoryFactory) :
            this(
                entityManagerFactoryFactory,
                HikariDataSourceFactory(),
                Duration.ofSeconds(3),
                { d -> Thread.sleep(d.toMillis()) })

    private companion object {
        private val logger = contextLogger()
    }

    private lateinit var lateInitialisedClusterDataSource: DataSource
    private lateinit var configFactory: SmartConfigFactory
    private val dbConnectionsEntityManagerFactory: EntityManagerFactory by lazy {
        // special case EMF for fetching other DB connections
        entityManagerFactoryFactory.create(
            "DB Connections",
            ConfigurationEntities.classes.toList(),
            DbEntityManagerConfiguration(lateInitialisedClusterDataSource)
            )
    }

    override val clusterDataSource: DataSource
        get() {
            if(!this::lateInitialisedClusterDataSource.isInitialized)
                throw DBConfigurationException("Cluster DB must be initialised.")
            return lateInitialisedClusterDataSource
        }

    /**
     * Initialise the [DbConnectionsRepositoryImpl] with the given Cluster DB config.
     *
     * This also validates we can connect to the configured cluster DB and retries until it is successful.
     */
    override fun initialise(config: SmartConfig) {
        // configure connection to cluster DB and try/retry to connect
        configFactory = config.factory
        lateInitialisedClusterDataSource = dataSourceFactory.createFromConfig(config)

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
                return
            } catch (e: DBConfigurationException) {
                logger.warn("Failed to connect to Cluster DB. " +
                        "Will be retrying in ${checkConnectionRetryTimeout.seconds}s: $e")
                sleeper(checkConnectionRetryTimeout)
            }
        }
    }

    override fun put(
        name: String,
        privilege: DbPrivilege,
        config: SmartConfig,
        description: String?,
        updateActor: String) {
        logger.debug("Saving $privilege DB connection for $name: ${config.root().render()}")
        dbConnectionsEntityManagerFactory.transaction {
            val configAsString = config.root().render(ConfigRenderOptions.concise())
            val existingConfig = it.findDbConnectionByNameAndPrivilege(name, privilege)?.apply {
                update(configAsString, description, updateActor)
            } ?: DbConnectionConfig(
                UUID.randomUUID(),
                name,
                privilege,
                Instant.now(),
                updateActor,
                description,
                configAsString
            )
            it.persist(existingConfig)
            it.flush()
        }
    }

    override fun get(name: String, privilege: DbPrivilege): DataSource? {
        if(!this::configFactory.isInitialized)
            throw DBConfigurationException("Cluster DB must be initialised.")

        logger.debug("Fetching DB connection for $name")
        dbConnectionsEntityManagerFactory.createEntityManager().use {
            val dbConfig = it.findDbConnectionByNameAndPrivilege(name, privilege) ?:
                return null

            val config = ConfigFactory.parseString(dbConfig.config)
            logger.debug("Creating DB (${dbConfig.description}) from config: $config")
            return dataSourceFactory.createFromConfig(configFactory.create(config))
        }
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

