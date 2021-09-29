package net.corda.db.core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

class HikariDataSourceFactory(
    private val hikariDataSourceFactory: (c: HikariConfig) -> HikariDataSource = { c ->
        HikariDataSource(c)
    }
) : DataSourceFactory {
    override fun create(
        driverClass: String,
        jdbcUrl: String,
        username: String,
        password: String,
        isAutoCommit: Boolean,
        maximumPoolSize: Int
    ): DataSource {
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
