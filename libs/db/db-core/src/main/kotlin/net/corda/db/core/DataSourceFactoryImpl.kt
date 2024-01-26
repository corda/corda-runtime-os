package net.corda.db.core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory
import com.zaxxer.hikari.util.DriverDataSource
import io.micrometer.core.instrument.Metrics
import java.io.Closeable
import java.sql.Connection
import java.time.Duration
import java.util.Properties
import javax.sql.DataSource

/**
 * Creates [DataSource] instances.
 *
 * If using OSGi, we defer to the OSGi service registry and use it to find
 * instances of [org.osgi.service.jdbc.DataSourceFactory] and use that to create the
 * [DataSource] instead.
 *
 * If not, we use Hikari's DriverDataSource, which, under the covers uses [java.sql.DriverManager].
 *
 * DataSources default to being pools but can be requested to be individual connections.
 * In case of pooled connections, closing the pool will close all connections in the pool, closing the
 * connection will just return the connection to the pool.
 * In case of non-pooled connections, they will be closed immediately when a connection is closed. Closing
 * the [CloseableDataSource] does not have any impact in this case.
 */
class DataSourceFactoryImpl(
    private val hikariDataSourceFactory: (c: HikariConfig) -> CloseableDataSource = { c ->
        val ds = HikariDataSource(c)
        ds.metricsTrackerFactory = MicrometerMetricsTrackerFactory(Metrics.globalRegistry)
        HikariDataSourceWrapper(ds)
    },
    private val driverDataSourceFactory:
        (
        jdbcUrl: String,
        driverClass: String,
        username: String,
        password: String,
    ) -> DriverDataSource = { jdbcUrl, driverClass, username, password ->
        val props = Properties()
        DriverDataSource(
            jdbcUrl,
            driverClass,
            props,
            username,
            password,
        )
    },
) : DataSourceFactory {
    /**
     * [HikariDataSource] wrapper that makes it [CloseableDataSource]
     */
    private class HikariDataSourceWrapper(
        private val delegate: HikariDataSource,
    ) : CloseableDataSource,
        Closeable by delegate, DataSource by delegate

    /**
     * [DataSourceWrapper] should only be used in a non-OSGi context.
     * Sets the autocommit and readonly flags when creating a connection.
     */
    private class DataSourceWrapper(
        private val delegate: DataSource,
        private val isAutoCommit: Boolean,
        private val isReadOnly: Boolean
    ) : CloseableDataSource, Closeable,
        DataSource by delegate {
        override fun getConnection(): Connection {
            return delegate.connection.also {
                it.autoCommit = isAutoCommit
                it.isReadOnly = isReadOnly
            }
        }
        override fun close() {}
    }

    override fun create(
        enablePool: Boolean,
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
        validationTimeout: Duration,
    ): CloseableDataSource {
        val dataSource = try {
            // Create and *wrap* an existing data source.
            OSGiDataSourceFactory.create(
                driverClass,
                jdbcUrl,
                username,
                password,
            )
        } catch (_: UnsupportedOperationException) {
            // Defer to Hikari's `DriverDataSource`, and hence java.sql.DriverManager, which we don't want in production
            // code. This part should only be hit in unit tests that don't use an OSGi framework.
            driverDataSourceFactory(
                jdbcUrl,
                driverClass,
                username,
                password
            )
        }

        if (!enablePool) return DataSourceWrapper(dataSource, isAutoCommit, isReadOnly)

        val conf = HikariConfig()
        conf.dataSource = dataSource
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
        if (Duration.ZERO != keepaliveTime)
            conf.keepaliveTime = keepaliveTime.toMillis()
        conf.validationTimeout = validationTimeout.toMillis()

        return hikariDataSourceFactory(conf)
    }
}
