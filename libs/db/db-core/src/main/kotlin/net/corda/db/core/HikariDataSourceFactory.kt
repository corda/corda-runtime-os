package net.corda.db.core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.Closeable
import java.time.Duration
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
        val ds = HikariDataSource(c)
        // TODO - this can be enabled when https://github.com/brettwooldridge/HikariCP/pull/1989 is released
        //   https://r3-cev.atlassian.net/browse/CORE-7113
        // ds.metricsTrackerFactory = MicrometerMetricsTrackerFactory(MeterFactory.registry)
        DataSourceWrapper(ds)
    }
) : DataSourceFactory {
    /**
     * [HikariDataSource] wrapper that makes it [CloseableDataSource]
     */
    private class DataSourceWrapper(private val delegate: HikariDataSource)
        : CloseableDataSource, Closeable by delegate, DataSource by delegate

    override fun create(
        driverClass: String,
        jdbcUrl: String,
        username: String,
        password: String,
        isAutoCommit: Boolean,
        isReadOnly: Boolean,
        maximumPoolSize: Int,
        minimumPoolSize: Int?,
        idleTimeout: Duration,
        maxLifetime: Duration,
        keepaliveTime: Duration,
        validationTimeout: Duration
    ): CloseableDataSource {
        val conf = HikariConfig()

        try {
            // Create and *wrap* an existing data source.
            conf.dataSource = OSGiDataSourceFactory.create(
                driverClass,
                jdbcUrl,
                username,
                password
            )
        } catch (_: UnsupportedOperationException) {
            // Defer to Hikari, and hence java.sql.DriverManager, which we don't want in production
            // code. This part should only be hit in unit tests that don't use an OSGi framework.
            conf.driverClassName = driverClass
            conf.jdbcUrl = jdbcUrl
            conf.username = username
            conf.password = password
        }

        conf.isAutoCommit = isAutoCommit
        conf.isReadOnly = isReadOnly
        conf.maximumPoolSize = maximumPoolSize
        if (minimumPoolSize != null) {
            conf.minimumIdle = minimumPoolSize
        } else {
            conf.minimumIdle = maximumPoolSize
        }
        if (conf.minimumIdle != conf.maximumPoolSize) {
            conf.idleTimeout = idleTimeout.toMillis()
        } else {
            conf.idleTimeout = 0
        }
        conf.maxLifetime = maxLifetime.toMillis()
        if(Duration.ZERO != keepaliveTime)
            conf.keepaliveTime = keepaliveTime.toMillis()
        conf.validationTimeout = validationTimeout.toMillis()

        return hikariDataSourceFactory(conf)
    }
}
