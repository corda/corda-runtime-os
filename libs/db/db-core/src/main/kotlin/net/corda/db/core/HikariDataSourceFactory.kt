package net.corda.db.core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.sql.Connection
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
        : CloseableDataSource, Closeable, DataSource by delegate {
        private companion object {
            private val logger = LoggerFactory.getLogger("PPP")
            private val usedWrappers = ConcurrentLinkedDeque<DataSourceWrapper>()
            private val maxSize = AtomicInteger()
            fun report() {
                val size = usedWrappers.map {
                    it.delegate.hikariPoolMXBean.totalConnections
                }.sum()
                logger.info("We have ${usedWrappers.size} datasources and $size connections max so far is $maxSize")
                usedWrappers.forEach {
                    it.report()
                }
            }
            init {
                Executors.newScheduledThreadPool(1, DaemonFactory).also {
                    it.scheduleAtFixedRate(::report, 60, 10, TimeUnit.SECONDS)
                }
            }
        }

        private val created = Exception("QQQ")

        init {
            usedWrappers.add(this)
        }

        override fun close() {
            usedWrappers.remove(this)
        }

        override fun getConnection(): Connection {
            return MyConnection(delegate.connection)
        }

        override fun getConnection(username: String?, password: String?): Connection {
            return MyConnection(delegate.getConnection(username, password))
        }

        private inner class MyConnection(
            val connection: Connection
        ) : Connection by connection {
            val created = Exception(connection.metaData.url)
            init {
                val size = usedWrappers.map {
                    it.delegate.hikariPoolMXBean.totalConnections
                }.sum()
                logger.info("Creating connection ${hashCode()} for ${this@DataSourceWrapper.hashCode()} size is $size", created)
                if (size > maxSize.get()) {
                    logger.info("New max size!!! $size", created)
                    maxSize.set(size)
                }
            }
            override fun close() {
                logger.info(
                    "Closing connection ${hashCode()} for ${this@DataSourceWrapper.hashCode()}",
                    Exception("QQQ",
                        created))
                connection.close()
            }
        }
        private fun report() {
            val pool = delegate.hikariPoolMXBean
            val config = delegate.hikariConfigMXBean
            logger.info("Datasource: ${hashCode()} created by:", created)
            logger.info("\t activeConnections: ${pool.activeConnections}," +
                    " totalConnection: ${pool.totalConnections}, " +
                    "idleConnection: ${pool.idleConnections}")
            logger.info("\t idleTimeout: ${config.idleTimeout}, " +
                    "min: ${config.minimumIdle}," +
                    " pool size: ${config.maximumPoolSize}")
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

    private object DaemonFactory: ThreadFactory {
        override fun newThread(r: Runnable): Thread {
            return Thread(r).also {
                it.isDaemon = true
            }
        }
    }
}
