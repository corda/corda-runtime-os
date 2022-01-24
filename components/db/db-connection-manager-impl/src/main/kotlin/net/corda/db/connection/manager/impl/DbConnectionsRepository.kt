package net.corda.db.connection.manager.impl

import net.corda.db.core.DataSourceFactory
import net.corda.db.core.HikariDataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.util.contextLogger
import java.time.Duration
import java.util.UUID
import javax.sql.DataSource

/**
 * Repository for DB connections fetched from the Connections DB.
 *
 * Throws exception when trying to fetch a connection before the Cluster connection has been initialised.
 */
class DbConnectionsRepository(
    private val dataSourceFactory: DataSourceFactory = HikariDataSourceFactory(),
    private val checkConnectionRetryTimeout: Duration = Duration.ofSeconds(3),
    private val sleeper: (d: Duration) -> Unit = { d -> Thread.sleep(d.toMillis()) }
) {

    private companion object {
        private val logger = contextLogger()
    }

    var isInitialised = false
    lateinit var clusterDataSource: DataSource

    /**
     * Initialise the [DbConnectionsRepository] with the given Cluster DB config.
     *
     * This also validates we can connect to the configured cluster DB and retries until it is successful.
     */
    fun initialise(config: SmartConfig) {
        // configure connection to cluster DB and try/retry to connect
        clusterDataSource = dataSourceFactory.createFromConfig(config)
        isInitialised = true

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

    fun put(connectionID: UUID, config: SmartConfig) {
        logger.debug("Saving DB connection for $connectionID: ${config.root().render()}")
        TODO("Not yet implemented")
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

