package net.corda.db.core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    companion object {
        private val logger = LoggerFactory.getLogger("TTT")
        private val repoter = Executors.newScheduledThreadPool(1)
    }
    /**
     * [HikariDataSource] wrapper that makes it [CloseableDataSource]
     */
    private class DataSourceWrapper(val delegate: HikariDataSource)
        : CloseableDataSource, Closeable by delegate, DataSource by delegate {
            val myReport = repoter.scheduleAtFixedRate(
                {
                    val bean = delegate.hikariConfigMXBean
                    val info = delegate.hikariPoolMXBean
                    logger.info("Reporting on ${delegate.poolName}")
                    logger.info("\t minimumIdle = ${bean.minimumIdle}")
                    logger.info("\t idleTimeout = ${bean.idleTimeout}")
                    logger.info("\t active = ${info.activeConnections}")
                    logger.info("\t total = ${info.totalConnections}")
                },
                3,
                1,
                TimeUnit.MINUTES,
                )

        override fun close() {
            myReport.cancel(false)
            delegate.close()
        }
        }

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

        val ds = try {
            OSGiDataSourceFactory.create(
                driverClass,
                jdbcUrl,
                username,
                password
            )
        } catch (_: UnsupportedOperationException) {
            SimpleDataSource(
                driverClass,
                jdbcUrl,
                username,
                password
            )
        }
        val lds = LoggedDataSource(ds)
        conf.dataSource = lds
        conf.poolName = lds.toString()

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
        logger.info("Creating ds ${conf.dataSource} " +
                "idleTimeout = ${conf.idleTimeout}, " +
                "minimumIdle = ${conf.minimumIdle} " +
                "keepaliveTime = ${conf.keepaliveTime}")

        return hikariDataSourceFactory(conf)
    }
}
