package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.db.core.DataSourceFactory
import net.corda.db.core.HikariDataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import java.time.Duration
import java.util.UUID
import javax.sql.DataSource

/**
 * Repository for DB connections fetched from the Connections DB.
 *
 * Throws exception when trying to fetch a connection before the Cluster connection has been initialised.
 */
@Component(service = [DbConnectionsRepository::class])
class DbConnectionsRepositoryImpl(
    private val dataSourceFactory: DataSourceFactory = HikariDataSourceFactory(),
    private val checkConnectionRetryTimeout: Duration = Duration.ofSeconds(3),
    private val sleeper: (d: Duration) -> Unit = { d -> Thread.sleep(d.toMillis()) }
): DbConnectionsRepository {

    private companion object {
        private val logger = contextLogger()
    }

    lateinit var lateInitialisedClusterDataSource: DataSource

    /**
     * Initialise the [DbConnectionsRepositoryImpl] with the given Cluster DB config.
     *
     * This also validates we can connect to the configured cluster DB and retries until it is successful.
     */
    override fun initialise(config: SmartConfig) {
        // configure connection to cluster DB and try/retry to connect
        lateInitialisedClusterDataSource = dataSourceFactory.createFromConfig(config)

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

    override fun put(connectionID: UUID, config: SmartConfig) {
        logger.debug("Saving DB connection for $connectionID: ${config.root().render()}")
        TODO("Not yet implemented")
    }

    override fun get(connectionID: UUID): DataSource {
        logger.debug("Get DB connection for $connectionID")
        TODO("Not yet implemented")
    }

    override val clusterDataSource: DataSource
        get() {
            if(!this::lateInitialisedClusterDataSource.isInitialized)
                throw DBConfigurationException("Cluster DB must be initialised.")
            return lateInitialisedClusterDataSource
        }

    /**
     * Checks that it is possible to connect to the cluster database using the [dataSource].
     *
     * @throws DBProcessorException If the cluster database cannot be connected to.
     */
    private fun checkDatabaseConnection(dataSource: DataSource) = try {
        dataSource.connection.close()
    } catch (e: Exception) {
        throw DBConfigurationException("Could not connect to cluster database.", e)
    }
}

