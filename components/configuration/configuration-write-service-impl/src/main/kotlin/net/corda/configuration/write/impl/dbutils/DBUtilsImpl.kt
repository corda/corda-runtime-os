package net.corda.configuration.write.impl.dbutils

import net.corda.configuration.write.impl.CONFIG_DB_DRIVER
import net.corda.configuration.write.impl.CONFIG_DB_PASS
import net.corda.configuration.write.impl.CONFIG_DB_USER
import net.corda.configuration.write.impl.CONFIG_JDBC_URL
import net.corda.configuration.write.impl.MAX_POOL_SIZE
import net.corda.db.core.HikariDataSourceFactory
import net.corda.libs.configuration.SmartConfig
import org.osgi.service.component.annotations.Component
import javax.sql.DataSource

/** An implementation of [DBUtils]. */
@Suppress("Unused")
@Component(service = [DBUtils::class])
internal class DBUtilsImpl : DBUtils {
    private var dataSource: DataSource? = null

    override fun checkClusterDatabaseConnection(config: SmartConfig) {
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