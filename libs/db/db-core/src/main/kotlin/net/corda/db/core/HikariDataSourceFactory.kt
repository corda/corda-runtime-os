package net.corda.db.core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.UUID
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger(HikariDataSourceFactory::class.java)

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

        val i = UUID.randomUUID().toString()
        logger.warn("HikariDataSource - $i - 1.created - ${stackTrace()}")
        DataSourceWrapper(ds, i)
    }
) : DataSourceFactory {
    /**
     * [HikariDataSource] wrapper that makes it [CloseableDataSource]
     */
    private class DataSourceWrapper(private val delegate: HikariDataSource, private val counter: String)
        : CloseableDataSource, Closeable, DataSource by delegate {
        override fun close() {
            logger.warn("HikariDataSource - $counter - 2.closed - ${stackTrace()}")
            delegate.close()
        }
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
        conf.maximumPoolSize = maximumPoolSize

        return hikariDataSourceFactory(conf)
    }
}

private fun stackTrace() = Throwable().stackTraceToString().replace("\n", ", ")
