package net.corda.db.core

import javax.sql.DataSource

class PostgresDataSourceFactory(
    private val datasourceFactory: DataSourceFactory = HikariDataSourceFactory()
) {
    fun create(
        jdbcUrl: String,
        username: String,
        password: String,
        isAutoCommit: Boolean = false,
        maximumPoolSize: Int = 10
    ): DataSource {
        return datasourceFactory.create(
            "org.postgresql.Driver",
            jdbcUrl,
            username,
            password,
            isAutoCommit,
            maximumPoolSize
        )
    }
}
