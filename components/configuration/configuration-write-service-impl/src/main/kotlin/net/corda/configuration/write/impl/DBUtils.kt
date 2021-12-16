package net.corda.configuration.write.impl

import net.corda.configuration.write.ConfigWriteServiceException
import net.corda.db.core.HikariDataSourceFactory
import net.corda.libs.configuration.SmartConfig
import org.osgi.service.component.annotations.Component
import java.sql.SQLException
import javax.sql.DataSource

/** Encapsulates database-related functionality, so that it can be stubbed during tests. */
@Component(service = [DBUtils::class])
class DBUtils {
    private val dataSourceFactory = HikariDataSourceFactory()

    /**
     * Checks that it's possible to connect to the cluster database using the [config].
     *
     * @throws ConfigWriteServiceException If the cluster database cannot be connected to.
     */
    fun checkClusterDatabaseConnection(config: SmartConfig) {
        val dataSource = createDataSource(config)
        try {
            dataSource.connection.close()
        } catch (e: SQLException) {
            throw ConfigWriteServiceException("Could not connect to cluster database.", e)
        }
    }

    /** Creates a [DataSource] for the cluster database using the [config]. */
    private fun createDataSource(config: SmartConfig): DataSource {
        val driver = config.getString(CONFIG_DB_DRIVER)
        val jdbcUrl = config.getString(CONFIG_JDBC_URL)
        val username = config.getString(CONFIG_DB_USER)
        val password = config.getString(CONFIG_DB_PASS)

        return dataSourceFactory.create(driver, jdbcUrl, username, password, false, MAX_POOL_SIZE)
    }
}