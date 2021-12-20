package net.corda.configuration.write.impl

import net.corda.db.core.HikariDataSourceFactory
import net.corda.libs.configuration.SmartConfig
import org.osgi.service.component.annotations.Component
import java.sql.SQLException
import javax.sql.DataSource

/** Encapsulates database-related functionality, so that it can be stubbed during tests. */
@Component(service = [DBUtils::class])
internal class DBUtils {
    private var dataSource: DataSource? = null

    /**
     * Checks that it's possible to connect to the cluster database using the [config].
     *
     * @throws SQLException If the cluster database cannot be connected to.
     */
    fun checkClusterDatabaseConnection(config: SmartConfig) {
        val dataSource = dataSource ?: setDataSource(config)
        dataSource.connection.close()
    }

    /** Sets [dataSource] using the [config]. */
    private fun setDataSource(config: SmartConfig): DataSource {
        val driver = config.getString(CONFIG_DB_DRIVER)
        val jdbcUrl = config.getString(CONFIG_JDBC_URL)
        val username = config.getString(CONFIG_DB_USER)
        val password = config.getString(CONFIG_DB_PASS)

        return HikariDataSourceFactory()
            .create(driver, jdbcUrl, username, password, false, MAX_POOL_SIZE)
            .also { dataSource ->
                this.dataSource = dataSource
            }
    }
}