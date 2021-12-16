package net.corda.configuration.write.impl

import com.typesafe.config.ConfigException
import net.corda.db.core.HikariDataSourceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.write.persistent.PersistentConfigWriterException
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
     * @throws PersistentConfigWriterException If the cluster database cannot be connected to.
     */
    fun checkClusterDatabaseConnection(config: SmartConfig) {
        val dataSource = createDataSource(config)
        try {
            dataSource.connection.close()
        } catch (e: SQLException) {
            throw PersistentConfigWriterException("Could not connect to cluster database.", e)
        }
    }

    /** Creates a [DataSource] for the cluster database using the [config]. */
    private fun createDataSource(config: SmartConfig): DataSource {
        val driver = getConfigStringOrDefault(config, CONFIG_DB_DRIVER, CONFIG_DB_DRIVER_DEFAULT)
        val jdbcUrl = getConfigStringOrDefault(config, CONFIG_JDBC_URL, CONFIG_JDBC_URL_DEFAULT)
        val username = getConfigStringOrDefault(config, CONFIG_DB_USER, CONFIG_DB_USER_DEFAULT)
        val password = getConfigStringOrDefault(config, CONFIG_DB_PASS, CONFIG_DB_PASS_DEFAULT)

        return dataSourceFactory.create(driver, jdbcUrl, username, password, false, MAX_POOL_SIZE)
    }

    /** Returns [path] from [config], or default if [path] does not exist. */
    private fun getConfigStringOrDefault(config: SmartConfig, path: String, default: String) = try {
        config.getString(path)
    } catch (e: ConfigException.Missing) {
        default
    }
}