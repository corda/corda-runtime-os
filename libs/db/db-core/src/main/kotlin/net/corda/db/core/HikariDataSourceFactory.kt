package net.corda.db.core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.PrintWriter
import java.sql.Connection

class HikariDataSourceFactory(
    private val hikariDataSourceFactory: (c: HikariConfig) -> CloseableDataSource = { c ->
        object: CloseableDataSource {
            val delegate = HikariDataSource(c)

            override fun close() = delegate.close()

            override fun getLogWriter() = delegate.logWriter

            override fun setLogWriter(out: PrintWriter?) {
                delegate.logWriter = out
            }

            override fun getLoginTimeout() = delegate.loginTimeout

            override fun setLoginTimeout(seconds: Int) {
                delegate.loginTimeout = seconds
            }

            override fun getParentLogger() = delegate.parentLogger

            override fun <T : Any?> unwrap(iface: Class<T>?): T = delegate.unwrap(iface)

            override fun isWrapperFor(iface: Class<*>?): Boolean = delegate.isWrapperFor(iface)

            override fun getConnection(): Connection = delegate.connection

            override fun getConnection(username: String?, password: String?): Connection =
                delegate.getConnection(username, password)
        }
    }
) : DataSourceFactory {
    override fun create(
        driverClass: String,
        jdbcUrl: String,
        username: String,
        password: String,
        isAutoCommit: Boolean,
        maximumPoolSize: Int
    ): CloseableDataSource {
        val conf = HikariConfig()
        conf.driverClassName = driverClass
        conf.jdbcUrl = jdbcUrl
        conf.username = username
        conf.password = password
        conf.isAutoCommit = isAutoCommit
        conf.maximumPoolSize = maximumPoolSize
        return hikariDataSourceFactory(conf)
    }
}
