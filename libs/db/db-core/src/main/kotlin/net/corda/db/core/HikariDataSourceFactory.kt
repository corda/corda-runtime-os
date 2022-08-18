package net.corda.db.core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

/**
 * Creates Hikari [DataSource] instances.
 *
 * If using OSGi, we defer to the OSGi service registry and use it to find
 * instances of [org.osgi.service.jdbc.DataSourceFactory] and use that to create the
 * [DataSource] instead.
 *
 * If not, we use Hikari, which, under the covers uses [java.sql.DriverManager].
 */
class HikariDataSourceFactory(
    private val hikariDataSourceFactory: (c: HikariConfig) -> CloseableDataSource = { c ->
        DataSourceWrapper(HikariDataSource(c))
    }
) : DataSourceFactory {
    /**
     * [HikariDataSource] wrapper that makes it [CloseableDataSource]
     */
    private class DataSourceWrapper(private val delegate: HikariDataSource): CloseableDataSource, DataSource by delegate {
        override fun close() = delegate.close()
    }

    override fun create(
        driverClass: String,
        jdbcUrl: String,
        username: String,
        password: String,
        isAutoCommit: Boolean,
        maximumPoolSize: Int
    ): CloseableDataSource {
        val conf = HikariConfig()

        if (OSGiDataSourceFactory.runningInOSGiFramework()) {
            // Create and *wrap* an existing data source.
            conf.dataSource = OSGiDataSourceFactory.create(
                driverClass,
                jdbcUrl,
                username,
                password
            )
        } else {
            // Defer to Hikari, and hence java.sql.DriverManager, which we don't want in production
            // code.  This part should only be hit in unit tests.
            conf.driverClassName = driverClass
            conf.jdbcUrl = jdbcUrl
            conf.username = username
            conf.password = password
        }

        conf.isAutoCommit = isAutoCommit
        conf.maximumPoolSize = maximumPoolSize

        return hikariDataSourceFactory(conf)
    }
}
