package net.corda.db.core

import javax.sql.DataSource

interface DataSourceFactory {
    @Suppress("LongParameterList")
    fun create(
        driverClass: String,
        jdbcUrl: String,
        username: String,
        password: String,
        isAutoCommit: Boolean = false,
        maximumPoolSize: Int = 10
    ): DataSource
}
